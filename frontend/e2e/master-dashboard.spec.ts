import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

/**
 * F1.6 end to end: co-masters and the work tray.
 *
 * Three things no unit test can see, because each of them crosses the backend and the frontend:
 * that a co-master added on one screen really gains the table on another; that removing them takes
 * it away again — the bug the `MasterRowStatus` filter fixes, which membership answered wrong while
 * `Deleted` only ever came from a deleted table; and that `/master` lists work and empties itself
 * when the work is done.
 *
 * Login through TestLoginController (the backend's "test" profile), like the rest of the suite.
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

/** Creates a table through the wizard, walking past the steps that ask for nothing required. */
async function createTableThroughWizard(page: Page, name: string) {
  await page.goto('/master/tables/new')
  await page.getByRole('textbox', { name: 'Nombre' }).fill(name)
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await page.getByRole('button', { name: 'Crear mesa' }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()
  return page.url().split('/master/tables/')[1]
}

/**
 * A co-master added gains the table; a co-master removed loses it in the same breath.
 *
 * The removal half is the one that matters: membership used to be answered from the master row
 * without looking at its status, so a person taken off a table kept every permission on it while
 * the screens stopped listing them.
 */
test('a master adds a co-master, who sees the table, and loses it when removed', async ({ browser }) => {
  const masterDiscordId = `e2e-cm-master-${runId}`
  const coMasterDiscordId = `e2e-cm-co-${runId}`
  const tableName = `Mesa Co-master E2E ${runId}`

  // The co-master has to exist before it can be searched for; the test login creates them. Note they
  // are created *without* the Master role: running a table is membership, not the role (#135).
  const coMasterContext = await browser.newContext()
  await testLogin(coMasterContext.request, coMasterDiscordId)
  await coMasterContext.close()

  const master = await newAuthenticatedPage(browser, masterDiscordId, true, false)
  const coMaster = await newAuthenticatedPage(browser, coMasterDiscordId, false, false)
  try {
    const tableId = await createTableThroughWizard(master.page, tableName)

    await master.page.getByRole('link', { name: 'Jugadores' }).click()
    const search = master.page.getByRole('combobox', { name: 'Buscar personas' })
    await search.fill(coMasterDiscordId)
    await master.page.getByRole('button', { name: new RegExp(`^${coMasterDiscordId}\\b`) }).click()
    await expect(master.page.getByText('Co-master', { exact: true })).toBeVisible()

    // Membership and not the platform role: somebody with no Master role runs this table and sees it.
    await coMaster.page.goto('/master/tables')
    await expect(coMaster.page.getByText(tableName)).toBeVisible()
    await coMaster.page.goto(`/master/tables/${tableId}`)
    await expect(coMaster.page.getByRole('heading', { name: tableName })).toBeVisible()

    await master.page.getByRole('button', { name: `Quitar a ${coMasterDiscordId}` }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
    await expect(master.page.getByText('Co-master', { exact: true })).toBeHidden()

    // The row survives as a record, but it is no longer a permission: the management screen refuses.
    await coMaster.page.goto(`/master/tables/${tableId}`)
    await expect(coMaster.page.getByText('No tenés permiso')).toBeVisible()
    await coMaster.page.goto('/master/tables')
    await expect(coMaster.page.getByText(tableName)).toBeHidden()
  } finally {
    await coMaster.context.close()
    await master.context.close()
  }
})

/**
 * The tray, from full to empty: an admin sends a draft back, the master sees the row on `/master`,
 * corrects the table through the edit form, resubmits, and the row is gone.
 *
 * It is also the only end-to-end path through the `PUT` — which had had an endpoint, a hook and a
 * type since F1.2 and no screen at all, so a table in `ChangesRequested` could not be corrected.
 */
test('a table sent back for changes shows on the tray until the master corrects it', async ({ browser }) => {
  const masterDiscordId = `e2e-tray-master-${runId}`
  const adminDiscordId = `e2e-tray-admin-${runId}`
  const tableName = `Mesa Bandeja E2E ${runId}`
  const correctedName = `${tableName} corregida`

  const master = await newAuthenticatedPage(browser, masterDiscordId, true, false)
  try {
    // A fresh master has nothing waiting, and the tray has to say that as good news.
    await master.page.goto('/master')
    await expect(master.page.getByText('Nada espera tu respuesta')).toBeVisible()

    const tableId = await createTableThroughWizard(master.page, tableName)

    const admin = await newAuthenticatedPage(browser, adminDiscordId, false, true)
    try {
      await admin.page.goto('/admin/tables')
      const row = admin.page.getByRole('listitem').filter({ hasText: tableName })
      await row.getByRole('button', { name: 'Pedir cambios' }).click()
      const dialog = admin.page.getByRole('dialog')
      await dialog.getByRole('textbox').fill('Falta la agenda semanal.')
      await dialog.getByRole('button', { name: 'Pedir cambios' }).click()
      await expect(admin.page.getByText('Con cambios pedidos', { exact: true }).first()).toBeVisible()
    } finally {
      await admin.context.close()
    }

    await master.page.goto('/master')
    const trayRow = master.page.getByRole('listitem').filter({ hasText: tableName })
    await expect(trayRow.getByText('Un admin pidió correcciones')).toBeVisible()

    // "Resolve" lands on the screen that resolves it, which for this kind is the edit form.
    await trayRow.getByRole('link', { name: 'Resolver' }).click()
    await expect(master.page).toHaveURL(new RegExp(`/master/tables/${tableId}/edit$`))
    await master.page.getByRole('textbox', { name: 'Nombre' }).fill(correctedName)
    await master.page.getByRole('button', { name: 'Guardar cambios' }).click()
    await expect(master.page.getByRole('heading', { name: correctedName })).toBeVisible()

    await master.page.goto(`/master/tables/${tableId}/status`)
    await master.page.getByRole('button', { name: 'Reenviar a revisión' }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
    await expect(master.page.getByText('En preparación', { exact: true })).toBeVisible()

    // Back to good news: waiting on an admin is not the master's work.
    await master.page.goto('/master')
    await expect(master.page.getByText('Nada espera tu respuesta')).toBeVisible()
  } finally {
    await master.context.close()
  }
})
