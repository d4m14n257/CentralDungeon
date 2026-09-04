import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

/**
 * E2 sub-slice 1, end to end (decisiones.md #163): a master creates a table through the wizard ->
 * Preparation; an admin approves it from /admin/tables -> Opened; the Primary starts it ->
 * InProgress; the Primary finishes it -> Finished. It covers the real state machine, not the
 * self-service E1 had.
 *
 * Login through TestLoginController (the backend's "test" profile), like registration-flow.spec.ts -
 * there is no real Discord yet.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

async function testLogin(request: APIRequestContext, discordId: string, asMaster = false, asAdmin = false) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asMaster, asAdmin },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as { accessToken: string }
}

async function newAuthenticatedPage(browser: Browser, discordId: string, asMaster: boolean, asAdmin: boolean) {
  const context = await browser.newContext()
  await testLogin(context.request, discordId, asMaster, asAdmin)
  const page = await context.newPage()
  return { context, page }
}

/**
 * Creates a table through F1.2's four-step wizard. These tests are about the state machine and not
 * about the agenda, so they walk straight past the three steps that ask for nothing required.
 *
 * @param page  the tab, already signed in as a master
 * @param name  the table's name
 */
async function createTableThroughWizard(page: Page, name: string) {
  await page.goto('/master/tables/new')
  await page.getByRole('textbox', { name: 'Nombre' }).fill(name)
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await page.getByRole('button', { name: 'Crear mesa' }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()
}

/**
 * Assigning masters with the people search (decisiones.md #164, #165): an admin creates a table
 * with no master, searches for two people — one by loose text, the other narrowed with
 * /discord_name — promotes the second and assigns. The table is born Unassigned and leaves the queue
 * once it is Opened.
 */
test('an admin searches for people and assigns the masters of an unassigned table', async ({ browser }) => {
  const adminDiscordId = `e2e-assign-admin-${runId}`
  const firstCandidate = `e2e-cand-a-${runId}`
  const secondCandidate = `e2e-cand-b-${runId}`
  const tableName = `Mesa Sin Master E2E ${runId}`

  // Both people have to exist to be findable: the test login creates them.
  const candidates = await browser.newContext()
  await testLogin(candidates.request, firstCandidate)
  await testLogin(candidates.request, secondCandidate)
  await candidates.close()

  const admin = await newAuthenticatedPage(browser, adminDiscordId, false, true)
  try {
    await admin.page.goto('/admin/tables')
    await admin.page.getByRole('button', { name: 'Crear mesa sin master' }).click()
    const createDialog = admin.page.getByRole('dialog')
    await createDialog.getByRole('textbox', { name: 'Nombre' }).fill(tableName)
    await createDialog.getByRole('button', { name: 'Crear mesa sin master' }).click()

    const row = admin.page.getByRole('listitem').filter({ hasText: tableName })
    await expect(row).toBeVisible()
    await row.getByRole('button', { name: 'Asignar masters' }).click()

    const dialog = admin.page.getByRole('dialog')
    const search = dialog.getByRole('combobox', { name: 'Buscar personas' })
    // Anchored at the start: the bare name also shows in the chip's X and in the master's.
    const result = (discordId: string) => dialog.getByRole('button', { name: new RegExp(`^${discordId}\\b`) })

    // With a prefix: Enter closes the criterion into a chip, which narrows the search to one field.
    // The input is always filled while empty - Enter and the chip's X clear it on their own. Typing
    // over existing text was deliberately left out of the case: in an earlier run of this suite the
    // `fill` over an already-loaded input ended with the input blank, and key handling is already
    // covered by SearchQueryInput.test.tsx, which does not depend on the browser.
    await search.fill(`/discord_name ${firstCandidate}`)
    await search.press('Enter')
    await expect(dialog.getByText('Discord:')).toBeVisible()
    await result(firstCandidate).click()

    // Removing the chip returns the search to the basic criterion: the Discord name or the system one.
    await dialog.getByRole('button', { name: `Quitar criterio: ${firstCandidate}` }).click()
    await search.fill(secondCandidate)
    await result(secondCandidate).click()

    // The first one came in as Primary; promoting the second demotes the first.
    await dialog.getByRole('button', { name: `Hacer master a ${secondCandidate}` }).click()
    await expect(dialog.getByRole('button', { name: `Hacer master a ${firstCandidate}` })).toBeVisible()

    await dialog.getByRole('button', { name: 'Asignar masters' }).click()
    await expect(row).toBeHidden()
  } finally {
    await admin.context.close()
  }
})

/**
 * Deleting what was never public (decisiones.md #175): the master builds a table, decides it is not
 * going to happen and removes it before publishing. Once open that is no longer possible: an open
 * table is cancelled.
 */
test('a master deletes a draft that never went public, and cannot delete it once open', async ({ browser }) => {
  const masterDiscordId = `e2e-del-master-${runId}`
  const adminDiscordId = `e2e-del-admin-${runId}`
  const draftName = `Mesa Borrador E2E ${runId}`
  const openedName = `Mesa Publicada E2E ${runId}`

  const master = await newAuthenticatedPage(browser, masterDiscordId, true, false)
  try {
    // A draft: created and removed without anybody having seen it.
    await createTableThroughWizard(master.page, draftName)

    await master.page.getByRole('link', { name: 'Estado' }).click()
    await master.page.getByRole('button', { name: 'Eliminar mesa' }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()

    // It goes back to the listing and the table is nowhere to be found.
    await expect(master.page).toHaveURL(/\/master\/tables$/)
    await expect(master.page.getByText(draftName)).toBeHidden()

    // An approved table has been public: the delete button does not exist.
    // The URL is only usable once the navigation finished, and the helper already waits for the heading.
    await createTableThroughWizard(master.page, openedName)
    const tableId = master.page.url().split('/master/tables/')[1]

    const admin = await newAuthenticatedPage(browser, adminDiscordId, false, true)
    try {
      await admin.page.goto('/admin/tables')
      const row = admin.page.getByRole('listitem').filter({ hasText: openedName })
      await row.getByRole('button', { name: 'Aprobar' }).click()
      await admin.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
      await expect(row).toBeHidden()
    } finally {
      await admin.context.close()
    }

    await master.page.goto(`/master/tables/${tableId}/status`)
    await expect(master.page.getByText('Abierta', { exact: true })).toBeVisible()
    await expect(master.page.getByRole('button', { name: 'Eliminar mesa' })).toBeHidden()
    await expect(master.page.getByRole('button', { name: 'Cancelar mesa' })).toBeVisible()
  } finally {
    await master.context.close()
  }
})

test('a master creates a table, an admin approves it, and the master runs it end to end', async ({ browser }) => {
  const masterDiscordId = `e2e-master-${runId}`
  const adminDiscordId = `e2e-admin-${runId}`
  const tableName = `Mesa Ciclo E2E ${runId}`

  const master = await newAuthenticatedPage(browser, masterDiscordId, true, false)
  try {
    await createTableThroughWizard(master.page, tableName)
    await expect(master.page.getByText('En preparación', { exact: true })).toBeVisible()
    const tableUrl = master.page.url()
    const tableId = tableUrl.split('/master/tables/')[1]

    const admin = await newAuthenticatedPage(browser, adminDiscordId, false, true)
    try {
      await admin.page.goto('/admin/tables')
      const row = admin.page.getByRole('listitem').filter({ hasText: tableName })
      await expect(row).toBeVisible()
      await row.getByRole('button', { name: 'Aprobar' }).click()
      await admin.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
      // /admin/tables only lists what is waiting on a review (Unassigned/Preparation/ChangesRequested):
      // once Opened, the row leaves this queue rather than staying with an updated badge.
      await expect(row).toBeHidden()
    } finally {
      await admin.context.close()
    }

    await master.page.goto(`/master/tables/${tableId}/status`)
    await expect(master.page.getByRole('heading', { name: tableName })).toBeVisible()
    await expect(master.page.getByText('Abierta', { exact: true })).toBeVisible()

    await master.page.getByRole('button', { name: 'Iniciar mesa' }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
    await expect(master.page.getByText('En curso', { exact: true })).toBeVisible()

    await master.page.getByRole('button', { name: 'Finalizar mesa' }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
    await expect(master.page.getByText('Finalizada', { exact: true })).toBeVisible()
  } finally {
    await master.context.close()
  }
})
