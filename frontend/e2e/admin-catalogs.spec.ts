import { test, expect, type APIRequestContext, type Browser } from '@playwright/test'

/**
 * F1.1 end to end: catalog administration against the real backend and the real seed.
 *
 * What it proves is the half of the catalog design a unit test cannot see — that the synonym groups
 * of V3__catalog_seed.sql arrive already assembled at the screen (#54, #59), and that disabling a
 * value and restoring it puts everything back as it was without breaking anything (#81).
 *
 * Login through TestLoginController (the backend's `test` profile), like every other spec.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

/**
 * Signs in without going through Discord: the `test` profile's shortcut issues the same tokens the
 * real login does.
 *
 * @param request the browser's network context, so the refresh cookie lands in it
 * @param discordId which identity to sign in as
 * @param asAdmin whether to grant the Admin role as well
 */
async function testLogin(request: APIRequestContext, discordId: string, asAdmin = false) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asAdmin },
  })
  expect(response.ok()).toBeTruthy()
}

/**
 * Opens a tab that is already signed in.
 *
 * @param browser the test's browser
 * @param discordId which identity to sign in as
 * @param asAdmin whether to grant the Admin role as well
 * @returns the context — to close it — and the page
 */
async function newAuthenticatedPage(browser: Browser, discordId: string, asAdmin: boolean) {
  const context = await browser.newContext()
  await testLogin(context.request, discordId, asAdmin)
  const page = await context.newPage()
  return { context, page }
}

/**
 * The scenario F1.1 is measured by: an admin opens /admin/catalogs and sees the seed's synonym
 * groups already resolved — "DANDD" shows as an equivalent of "D&D 5e", which is what makes
 * searching by either of them find the same tables (#54, #56).
 */
test('an admin sees the seeded synonym groups resolved in the catalog screen', async ({ browser }) => {
  const { context, page } = await newAuthenticatedPage(browser, `e2e-cat-admin-${runId}`, true)

  await page.goto('/admin/catalogs')

  // It starts on Systems, which is where the seed's D&D group lives.
  await expect(page.getByRole('heading', { name: 'Catálogos' })).toBeVisible()

  await page.getByLabel('Buscar en el catálogo').fill('DANDD')

  // The alias's row names its canonical entry: the group arrives assembled from the backend, the screen does not
  // lo deduce.
  const aliasRow = page.getByRole('row', { name: /DANDD/ })
  await expect(aliasRow).toBeVisible()
  await expect(aliasRow).toContainText('D&D 5e')

  await context.close()
})

/**
 * The three tabs are the same catalog with a different table behind each, and the state lives in
 * the URL: a catalog row is something one admin sends to another (#164 in spirit).
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
 * #81 in full: disabling takes a value out of circulation without breaking any links, and restoring
 * it puts it back as it was. A standalone tag from the seed is used — one with no equivalents — so
 * that disabling does not have to choose a successor, which is the other path and has an integration
 * test of its own.
 */
test('an admin disables a catalog value and restores it', async ({ browser }) => {
  const { context, page } = await newAuthenticatedPage(browser, `e2e-cat-disable-${runId}`, true)

  await page.goto('/admin/catalogs?kind=tags')
  await page.getByLabel('Buscar en el catálogo').fill('Homebrew')

  const row = page.getByRole('row', { name: /Homebrew/ })
  await expect(row).toBeVisible()

  await row.getByRole('button', { name: 'Dar de baja' }).first().click()
  await expect(page.getByText('Las mesas que lo usan lo conservan')).toBeVisible()
  await page.getByRole('button', { name: 'Dar de baja' }).last().click()

  await expect(page.getByText('Se dio de baja «Homebrew»')).toBeVisible()
  await expect(row).toContainText('Dado de baja')

  // And it comes back: the value is still there for the admin, which is the difference between disabling and deleting.
  await row.getByRole('button', { name: 'Restaurar' }).first().click()
  await expect(page.getByText('Se restauró «Homebrew»')).toBeVisible()
  await expect(row).toContainText('Aceptado')

  await context.close()
})
