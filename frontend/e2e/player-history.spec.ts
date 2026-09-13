import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

import { applyToTable } from './helpers/application'
import { addScheduleSlot, chooseRequiredCatalogs, submitForReview } from './helpers/tableWizard'

/**
 * F2.4 end to end: a table that ends stops being «mine» and becomes history (#133a).
 *
 * **The claim is the move, not either screen.** `/player/my-tables` used to list a finished table
 * forever, because it filtered by the state of the *application* and never by the state of the
 * *table*. What has to be proven is that the same table is in exactly one of the two lists at a time
 * — and that the one it lands on carries what only that screen shows: when it closed and the final
 * attendance.
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
  await adminPage.goto('/admin/tables')
  const row = adminPage.getByRole('listitem').filter({ hasText: name })
  await row.getByRole('button', { name: 'Aprobar' }).click()
  await adminPage.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
  await expect(row).toBeHidden()
}

/**
 * The scenario #133a describes: the table ends and changes lists, without the player doing anything.
 */
test('a table that ends leaves «mis mesas» and lands in the history with its attendance', async ({ browser }) => {
  const tableName = `Mesa Historial E2E ${runId}`
  const playerDiscordId = `e2e-hist-player-${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-hist-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-hist-admin-${runId}`, false, true)
  const player = await newAuthenticatedPage(browser, playerDiscordId, false, false)

  try {
    const tableId = await createTable(master.page, tableName, '20:00')
    await open(master.page, admin.page, tableId, tableName)

    await player.page.goto(`/player/tables/${tableId}`)
    await applyToTable(player.page)

    await master.page.goto(`/master/tables/${tableId}`)
    const candidate = master.page.getByRole('listitem').filter({ hasText: playerDiscordId }).first()
    await candidate.getByRole('button', { name: 'Aceptar' }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()

    // While it runs, it is «mine» and the history is empty — the two lists never hold it at once.
    await player.page.goto('/player/my-tables')
    await expect(player.page.getByRole('link', { name: new RegExp(tableName) })).toBeVisible()
    await player.page.goto('/player/history')
    await expect(player.page.getByText(new RegExp(tableName))).toBeHidden()

    // The master runs it and closes it.
    await master.page.goto(`/master/tables/${tableId}/status`)
    await master.page.getByRole('button', { name: 'Iniciar mesa' }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
    await master.page.getByRole('button', { name: 'Finalizar mesa' }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
    await expect(master.page.getByText('Finalizada', { exact: true })).toBeVisible()

    // And now it has swapped sides, carrying what only the history shows: when it closed (#180).
    await player.page.goto('/player/my-tables')
    await expect(player.page.getByText(new RegExp(tableName))).toBeHidden()

    await player.page.goto('/player/history')
    const entry = player.page.getByRole('listitem').filter({ hasText: tableName })
    await expect(entry).toBeVisible()
    await expect(entry.getByText(/Cerrada el/)).toBeVisible()
  } finally {
    await player.context.close()
    await admin.context.close()
    await master.context.close()
  }
})

/** The history is reachable by navigating, which is the half F1's review found missing elsewhere. */
test('the history is reachable from the player navigation, not only by URL', async ({ browser }) => {
  const player = await newAuthenticatedPage(browser, `e2e-hist-nav-${runId}`, false, false)

  try {
    await player.page.goto('/player')
    await player.page.getByRole('link', { name: 'Historial' }).click()

    await expect(player.page).toHaveURL(/\/player\/history$/)
    // Nothing played yet reads as neutral news, never as a broken screen (frontend-diseno.md §5).
    await expect(player.page.getByText('Todavía no terminaste ninguna mesa')).toBeVisible()
  } finally {
    await player.context.close()
  }
})
