import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

import { addScheduleSlot, chooseRequiredCatalogs, submitForReview } from './helpers/tableWizard'
import { approveTableFromQueue } from './helpers/adminQueue'

/**
 * F2.2 end to end: the character sheet that goes with an application (#60 uso 2).
 *
 * **Three claims no unit test can make.** That picking a file stages it and the *send* button is what
 * uploads it (#238); that the master of the table can open what a candidate attached — the seventh
 * way a file becomes readable; and that withdrawing the application makes the file report «sin usar»
 * again (#247), which is the rule that keeps a withdrawn application from immunising a file against
 * the purge of #75 forever.
 *
 * Login through TestLoginController (the backend's `test` profile), like every other spec.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

/**
 * Signs in without going through Discord: the `test` profile's shortcut issues the same tokens the
 * real login does.
 *
 * @param request   the browser's network context, so the refresh cookie lands in it
 * @param discordId which identity to sign in as
 * @param asMaster  whether to sign in with the Master role
 * @param asAdmin   whether to sign in with the Admin role
 */
async function testLogin(request: APIRequestContext, discordId: string, asMaster = false, asAdmin = false) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asMaster, asAdmin },
  })
  expect(response.ok()).toBeTruthy()
}

/**
 * Opens a tab that is already signed in.
 *
 * @param browser   the test's browser
 * @param discordId which identity to sign in as
 * @param asMaster  whether to sign in with the Master role
 * @param asAdmin   whether to sign in with the Admin role
 * @returns the context - to close it - and the page
 */
async function newAuthenticatedPage(browser: Browser, discordId: string, asMaster: boolean, asAdmin: boolean) {
  const context = await browser.newContext()
  await testLogin(context.request, discordId, asMaster, asAdmin)
  const page = await context.newPage()
  return { context, page }
}

/**
 * Builds a table through the wizard and returns its id.
 *
 * @param page     the tab, authenticated as a master
 * @param name     the table's name
 * @param hourtime the weekly slot, so two tables in one spec do not collide under R1 (#178)
 */
async function createTable(page: Page, name: string, hourtime: string): Promise<string> {
  await page.goto('/master/tables/new')
  await page.getByRole('textbox', { name: 'Nombre' }).fill(name)
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await chooseRequiredCatalogs(page)
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await addScheduleSlot(page, hourtime)
  await page.getByRole('button', { name: 'Siguiente' }).click()

  // The files step (#228): nothing is required there, so it is walked past.
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await page.getByRole('button', { name: 'Crear mesa' }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()

  const id = page.url().split('/master/tables/')[1]
  expect(id).toBeTruthy()
  return id as string
}

/** Sends the table to review and approves it, which is what opens it to applications. */
async function open(masterPage: Page, adminPage: Page, tableId: string, name: string) {
  await submitForReview(masterPage, tableId)
  await approveTableFromQueue(adminPage, name)
}

/** A PDF small enough to be under the cap and real enough for the MIME whitelist to accept it. */
function pdf(name: string, content: string) {
  return { name, mimeType: 'application/pdf', buffer: Buffer.from(`%PDF-1.4 ${content}`) }
}

/**
 * The headline case: attached, reviewed before sending, opened by the master, and listed as a use in
 * the applicant's own library.
 */
test('a player applies with a character sheet, and the master opens it', async ({ browser }) => {
  const tableName = `Mesa Ficha E2E ${runId}`
  const sheet = `ficha-postulacion-${runId}.pdf`
  const playerDiscordId = `e2e-appfile-player-${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-appfile-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-appfile-admin-${runId}`, false, true)
  const player = await newAuthenticatedPage(browser, playerDiscordId, false, false)

  try {
    const tableId = await createTable(master.page, tableName, '20:00')
    await open(master.page, admin.page, tableId, tableName)

    await player.page.goto(`/player/tables/${tableId}`)
    await player.page.getByRole('button', { name: 'Postularme' }).click()
    const dialog = player.page.getByRole('dialog')

    await dialog.getByLabel('Mensaje para el master').fill('Llevo la ficha lista.')
    await dialog.locator('input[type="file"]').setInputFiles(pdf(sheet, runId))
    await expect(dialog.getByText(sheet)).toBeVisible()

    // Step one cannot send: the confirm button only exists on the review step, which is the whole
    // point of the second step (#238, #247) — an application cannot be corrected afterwards.
    await expect(dialog.getByRole('button', { name: 'Postularme' })).toBeHidden()

    await dialog.getByRole('button', { name: 'Siguiente' }).click()
    await expect(dialog.getByText('Llevo la ficha lista.')).toBeVisible()
    await expect(dialog.getByText(sheet)).toBeVisible()

    await dialog.getByRole('button', { name: 'Postularme' }).click()
    await expect(dialog).toBeHidden()

    // The seventh way a file becomes readable: the master of the table opens what a candidate sent.
    await master.page.goto(`/master/tables/${tableId}`)
    const candidate = master.page.getByRole('listitem').filter({ hasText: playerDiscordId }).first()
    await expect(candidate.getByText('Archivos adjuntos')).toBeVisible()
    await expect(candidate.getByText(sheet)).toBeVisible()

    // And the applicant sees where their own file ended up (#232): the fourth source of uses.
    await player.page.goto('/my/files')
    const row = player.page.getByRole('listitem').filter({ hasText: sheet })
    await expect(row).toBeVisible()
    await expect(row.getByText(new RegExp(tableName))).toBeVisible()
  } finally {
    await player.context.close()
    await admin.context.close()
    await master.context.close()
  }
})

/**
 * The rule that fails silently if it is wrong (#247). The rows of `registration_files` survive a
 * withdrawal — they are the record that the sheet was sent — but they stop counting as a **use**. Get
 * that filter wrong and a withdrawn application immunises the file against the purge of #75 forever,
 * with nothing on screen to show for it.
 */
test('withdrawing an application stops the file counting as a use, without deleting it', async ({ browser }) => {
  const tableName = `Mesa Retiro E2E ${runId}`
  const sheet = `ficha-retiro-${runId}.pdf`
  const master = await newAuthenticatedPage(browser, `e2e-appwd-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-appwd-admin-${runId}`, false, true)
  const player = await newAuthenticatedPage(browser, `e2e-appwd-player-${runId}`, false, false)

  try {
    // A different slot from the spec above: R1 refuses one master two live tables that overlap (#178).
    const tableId = await createTable(master.page, tableName, '17:00')
    await open(master.page, admin.page, tableId, tableName)

    await player.page.goto(`/player/tables/${tableId}`)
    await player.page.getByRole('button', { name: 'Postularme' }).click()
    const dialog = player.page.getByRole('dialog')
    await dialog.locator('input[type="file"]').setInputFiles(pdf(sheet, runId))
    await expect(dialog.getByText(sheet)).toBeVisible()
    await dialog.getByRole('button', { name: 'Siguiente' }).click()
    await dialog.getByRole('button', { name: 'Postularme' }).click()
    await expect(dialog).toBeHidden()

    await player.page.goto('/my/files')
    const row = player.page.getByRole('listitem').filter({ hasText: sheet })
    await expect(row.getByText(new RegExp(tableName))).toBeVisible()

    await player.page.goto('/player/applications')
    const application = player.page.getByRole('listitem').filter({ hasText: tableName }).first()
    await application.getByRole('button', { name: 'Retirar' }).click()
    // The confirm button is labelled "Retirar" too, not "Confirmar": `useConfirm` takes a
    // `confirmLabel`, and naming the action is better than a generic yes.
    await player.page.getByRole('dialog').getByRole('button', { name: 'Retirar' }).click()

    // The file is still in the library — withdrawing does not delete what was uploaded — but it is no
    // longer used anywhere, which is exactly what the library has to say.
    await player.page.goto('/my/files')
    const afterRow = player.page.getByRole('listitem').filter({ hasText: sheet })
    await expect(afterRow).toBeVisible()
    await expect(afterRow.getByText(new RegExp(tableName))).toBeHidden()
  } finally {
    await player.context.close()
    await admin.context.close()
    await master.context.close()
  }
})
