import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

/**
 * Catalog administration against the real backend and the real seed: the table of groups and the
 * canvas each group opens into (#275).
 *
 * What it proves is the half of the catalog design a unit test cannot see: the synonym groups of
 * V3__catalog_seed.sql arrive already assembled (#54, #59), a proposal floats on the canvas until
 * it is connected to a group, and disabling a value and restoring it puts everything back (#81).
 *
 * Login goes through TestLoginController (the backend's `test` profile), like every other spec.
 * Playwright's viewport is wider than `md`, so the canvas is what renders and the list it becomes on
 * a phone is `display:none`.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

/**
 * Signs in without going through Discord. The `test` profile's shortcut issues the same tokens the
 * real login does.
 *
 * @param request   the browser's network context, so the refresh cookie lands in it
 * @param discordId which identity to sign in as
 * @param asAdmin   whether to grant the Admin role as well
 * @returns the access token, for the calls the spec makes to the API directly
 */
async function testLogin(request: APIRequestContext, discordId: string, asAdmin = false) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asAdmin },
  })
  expect(response.ok()).toBeTruthy()
  return ((await response.json()) as { accessToken: string }).accessToken
}

/**
 * Opens a tab that is already signed in.
 *
 * @param browser   the test's browser
 * @param discordId which identity to sign in as
 * @param asAdmin   whether to grant the Admin role as well
 * @returns the context (to close it), the page and the access token
 */
async function newAuthenticatedPage(browser: Browser, discordId: string, asAdmin: boolean) {
  const context = await browser.newContext()
  const accessToken = await testLogin(context.request, discordId, asAdmin)
  const page = await context.newPage()
  return { context, page, accessToken }
}

/**
 * One node of the canvas, by the text it shows.
 *
 * @param page the admin's page
 * @param name the value's name
 */
function graphNode(page: Page, name: string) {
  return page.locator('.react-flow__node').filter({ hasText: name })
}

/**
 * Opens a group's canvas the way an admin does: search it in the table, click its name.
 *
 * @param page the admin's page
 * @param kind which catalog tab
 * @param name the group's name
 */
async function openGroup(page: Page, kind: string, name: string) {
  await page.goto(`/admin/catalogs?kind=${kind}`)
  await page.getByLabel('Buscar en el catálogo').fill(name)
  await page
    .getByRole('row', { name: new RegExp(name) })
    .getByRole('link', { name })
    .click()
  await expect(page.getByRole('heading', { name: `Grupo «${name}»` })).toBeVisible()
}

/**
 * The table lists groups, not values (#275): searching an alias brings up the group it belongs to
 * (#54), and opening it draws the alias hanging from its head.
 */
test('searching an alias finds its group, and the group opens as a canvas', async ({ browser }) => {
  const { context, page } = await newAuthenticatedPage(browser, `e2e-cat-admin-${runId}`, true)

  await page.goto('/admin/catalogs')
  await expect(page.getByRole('heading', { name: 'Catálogos' })).toBeVisible()
  await page.getByLabel('Buscar en el catálogo').fill('DANDD')

  // The row is the group, not the alias: the seed's D&D group arrives assembled from the backend.
  const groupRow = page.getByRole('row', { name: /D&D 5e/ })
  await expect(groupRow).toBeVisible()
  await expect(page.getByRole('row', { name: /^DANDD/ })).toBeHidden()

  await groupRow.getByRole('link', { name: 'D&D 5e' }).click()
  await expect(page).toHaveURL(/\/admin\/catalogs\/systems\//)
  await expect(graphNode(page, 'D&D 5e')).toBeVisible()
  await expect(graphNode(page, 'DANDD')).toBeVisible()
  // One edge per alias, from the alias to its head.
  await expect(page.locator('.react-flow__edge')).not.toHaveCount(0)

  await context.close()
})

/**
 * The three tabs are the same catalog with a different table behind each, and the state lives in
 * the URL: a catalog row is something one admin sends to another (#185).
 */
test('the chosen catalog and the search live in the URL', async ({ browser }) => {
  const { context, page } = await newAuthenticatedPage(browser, `e2e-cat-url-${runId}`, true)

  await page.goto('/admin/catalogs')
  await page.getByRole('tab', { name: 'Plataformas' }).click()

  await expect(page).toHaveURL(/kind=platforms/)
  await expect(page.getByRole('row', { name: /Discord/ })).toBeVisible()

  // Reloading with the URL in place has to leave the screen as it was, not go back to Systems.
  await page.reload()
  await expect(page.getByRole('tab', { name: 'Plataformas', selected: true })).toBeVisible()

  await context.close()
})

/**
 * The gesture the canvas exists for (#275): a proposal floats, connected to nothing, until the admin
 * drags it onto a group's head - and then it is an accepted synonym of that group (#55).
 */
test('connecting a floating proposal to a group accepts it into that group', async ({ browser }) => {
  const { context, page, accessToken } = await newAuthenticatedPage(browser, `e2e-cat-float-${runId}`, true)
  const headers = { Authorization: `Bearer ${accessToken}` }
  // The `E2E ` prefix is what the suite's cleanup deletes (#172): a proposal cannot be deleted from the
  // API, only rejected or disabled, so without it every run would leave one more row behind.
  const name = `E2E flotante ${runId}`

  const proposed = await context.request.post(`${BACKEND_URL}/api/v1/tags`, { headers, data: { name } })
  expect(proposed.ok()).toBeTruthy()

  await openGroup(page, 'tags', 'One-shot')

  const floating = graphNode(page, name)
  await expect(floating).toContainText('Conectalo a un grupo')

  await floating.locator('.react-flow__handle-right').dragTo(graphNode(page, 'One-shot').locator('.react-flow__handle-left'))

  await expect(page.getByText(`«${name}» ahora es equivalente a «One-shot»`)).toBeVisible()
  await expect(graphNode(page, name)).toContainText('Aceptado')
  await expect(graphNode(page, name)).not.toContainText('Conectalo a un grupo')

  await context.close()
})

/**
 * #81 in full, from the node's menu - the keyboard's way to everything the canvas does. A
 * standalone tag from the seed is used, one with no synonyms, so disabling it does not have to
 * choose a successor; that path has an integration test of its own.
 */
test('an admin disables a catalog value from its node and restores it', async ({ browser }) => {
  const { context, page } = await newAuthenticatedPage(browser, `e2e-cat-disable-${runId}`, true)

  await openGroup(page, 'tags', 'Homebrew')
  const node = graphNode(page, 'Homebrew')

  await page.getByRole('button', { name: 'Acciones de «Homebrew»' }).click()
  await page.getByRole('menuitem', { name: 'Dar de baja' }).click()
  await expect(page.getByText('Las mesas que lo usan lo conservan')).toBeVisible()
  await page.getByRole('dialog').getByRole('button', { name: 'Dar de baja' }).click()

  await expect(page.getByText('Se dio de baja «Homebrew»')).toBeVisible()
  await expect(node).toContainText('Dado de baja')

  // And it comes back: the value is still there for the admin, which is the difference between disabling and deleting.
  await page.getByRole('button', { name: 'Acciones de «Homebrew»' }).click()
  await page.getByRole('menuitem', { name: 'Restaurar' }).click()
  await expect(page.getByText('Se restauró «Homebrew»')).toBeVisible()
  await expect(node).toContainText('Aceptado')

  await context.close()
})
