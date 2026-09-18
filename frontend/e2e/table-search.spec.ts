import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

import { addScheduleSlot, chooseRequiredCatalogs } from './helpers/tableWizard'
import { approveTableFromQueue } from './helpers/adminQueue'

/**
 * F2.1 end to end: the explorer finds a table by a synonym of what it is labelled with.
 *
 * **This is the claim no other test can make.** The unit tests prove the routing, the integration
 * test proves the SQL, and neither of them proves that somebody typing `/table_system DANDD` into the
 * real box, against the real seed, ends up looking at a table labelled `D&D 5e`. The whole value of
 * #54 and #56 is that last sentence.
 *
 * Login through TestLoginController (the backend's `test` profile), like every other spec.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

/**
 * Signs in without going through Discord: the `test` profile's shortcut issues the same tokens the
 * real login does.
 *
 * @param request    the browser's network context, so the refresh cookie lands in it
 * @param discordId  which identity to sign in as
 * @param asMaster   whether to sign in with the Master role
 * @param asAdmin    whether to sign in with the Admin role
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
 * Builds a table through the wizard, labelled with the seed's canonical values.
 *
 * The catalogs matter here in a way they do not in the other specs: the table ends up labelled
 * `D&D 5e`, and the point of the test is searching for it by a word it does not carry.
 *
 * @param page the tab, authenticated as a master
 * @param name the table's name
 * @returns the new table's id
 */
async function createTable(page: Page, name: string): Promise<string> {
  await page.goto('/master/tables/new')
  await page.getByRole('textbox', { name: 'Nombre' }).fill(name)
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await chooseRequiredCatalogs(page)
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await addScheduleSlot(page, '21:00')
  await page.getByRole('button', { name: 'Siguiente' }).click()

  // The files step (#228): nothing is required there, so it is walked past.
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await page.getByRole('button', { name: 'Crear mesa' }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()

  const id = page.url().split('/master/tables/')[1]
  expect(id).toBeTruthy()
  return id as string
}

/** Sends the table to review and approves it, which is what puts it in the explorer. */
async function open(masterPage: Page, adminPage: Page, tableId: string, name: string) {
  // A table is born in Draft since #245 and only its master sees it: it has to be sent to review
  // before an admin has anything to approve.
  await masterPage.goto(`/master/tables/${tableId}/status`)
  await masterPage.getByRole('button', { name: 'Enviar a revisión' }).click()
  const confirm = masterPage.getByRole('dialog')
  await confirm.getByRole('button', { name: 'Confirmar' }).click()
  await expect(confirm).toBeHidden()

  await approveTableFromQueue(adminPage, name)
}

/** Types a criterion and closes it into a chip. Enter is the only thing that closes one (#240). */
async function search(page: Page, criterion: string) {
  const box = page.getByRole('combobox', { name: 'Buscar mesas' })
  await box.click()
  await box.fill(criterion)
  await box.press('Enter')
}

/**
 * The scenario F2.1 is measured by, and the reason the search language was designed (#164): a table
 * labelled with the canonical entry is found by an alias nobody rewrote into it (#56).
 */
test('the explorer finds a table by a synonym of the system it is labelled with', async ({ browser }) => {
  const tableName = `Mesa Buscador E2E ${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-search-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-search-admin-${runId}`, false, true)
  const player = await newAuthenticatedPage(browser, `e2e-search-player-${runId}`, false, false)

  try {
    const tableId = await createTable(master.page, tableName)
    await open(master.page, admin.page, tableId, tableName)

    await player.page.goto('/player')
    await expect(player.page.getByRole('heading', { name: tableName })).toBeVisible()

    // The table carries `D&D 5e`; `DANDD` is an alias of it in V3__catalog_seed.sql, and not a single
    // row of table_systems was touched to make this work (#56).
    await search(player.page, '/table_system DANDD')
    await expect(player.page.getByRole('heading', { name: tableName })).toBeVisible()

    // And a system the table is not labelled with rules it out, which is what says the filter is
    // doing something rather than being ignored.
    await player.page.reload()
    await search(player.page, '/table_system Call of Cthulhu')
    await expect(player.page.getByRole('heading', { name: tableName })).toBeHidden()
  } finally {
    await master.context.close()
    await admin.context.close()
    await player.context.close()
  }
})

/**
 * The failure mode that looks like success. A word that names no accepted value has to match nothing;
 * read as "no filter", one typo lists the whole platform and nobody notices it went wrong (#246).
 */
test('a system nobody has ever proposed matches no table, instead of listing them all', async ({ browser }) => {
  const player = await newAuthenticatedPage(browser, `e2e-search-typo-${runId}`, false, false)

  try {
    await player.page.goto('/player')
    await search(player.page, '/table_system pathfimder')

    await expect(player.page.getByText('Ninguna mesa coincide')).toBeVisible()
  } finally {
    await player.context.close()
  }
})

/**
 * What was searched survives a reload and can be linked to (#185), and the empty search box is the
 * listing of everything.
 */
test('the search lives in the URL and can be linked to', async ({ browser }) => {
  const player = await newAuthenticatedPage(browser, `e2e-search-url-${runId}`, false, false)

  try {
    await player.page.goto('/player')
    await search(player.page, '/table_tag Principiantes')

    await expect(player.page).toHaveURL(/[?&]q=/)

    const linked = player.page.url()
    await player.page.goto('/player')
    await player.page.goto(linked)
    await expect(player.page.getByRole('combobox', { name: 'Buscar mesas' })).toBeVisible()
    await expect(player.page.getByText('Principiantes')).toBeVisible()
  } finally {
    await player.context.close()
  }
})
