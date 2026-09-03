import { test, expect, type APIRequestContext, type Browser } from '@playwright/test'

/**
 * F1.1 de punta a punta: la administración de catálogos contra el backend y la semilla reales.
 *
 * Lo que prueba es la mitad del diseño de catálogos que no se ve en un test unitario — que los
 * grupos de sinónimos de V3__catalog_seed.sql llegan armados hasta la pantalla (#54, #59), y que
 * dar de baja un valor y restaurarlo lo devuelve todo a como estaba sin romper nada (#81).
 *
 * Login por TestLoginController (perfil `test` del backend), igual que el resto de los specs.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

/**
 * Entra sin pasar por Discord: el atajo del perfil `test` emite los mismos tokens que el login real.
 *
 * @param request el contexto de red del navegador, para que la cookie de refresh quede en él
 * @param discordId con qué identidad entrar
 * @param asAdmin si además se le da el rol Admin
 */
async function testLogin(request: APIRequestContext, discordId: string, asAdmin = false) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asAdmin },
  })
  expect(response.ok()).toBeTruthy()
}

/**
 * Abre una pestaña ya autenticada.
 *
 * @param browser el navegador del test
 * @param discordId con qué identidad entrar
 * @param asAdmin si además se le da el rol Admin
 * @returns el contexto —para cerrarlo— y la página
 */
async function newAuthenticatedPage(browser: Browser, discordId: string, asAdmin: boolean) {
  const context = await browser.newContext()
  await testLogin(context.request, discordId, asAdmin)
  const page = await context.newPage()
  return { context, page }
}

/**
 * El escenario con el que se mide F1.1: un admin abre /admin/catalogs y ve los grupos de sinónimos
 * de la semilla resueltos — «DANDD» aparece como equivalente de «D&D 5e», que es lo que hace que
 * buscar por cualquiera de los dos encuentre las mismas mesas (#54, #56).
 */
test('an admin sees the seeded synonym groups resolved in the catalog screen', async ({ browser }) => {
  const { context, page } = await newAuthenticatedPage(browser, `e2e-cat-admin-${runId}`, true)

  await page.goto('/admin/catalogs')

  // Arranca en Sistemas, que es donde vive el grupo de D&D de la semilla.
  await expect(page.getByRole('heading', { name: 'Catálogos' })).toBeVisible()

  await page.getByLabel('Buscar en el catálogo').fill('DANDD')

  // La fila del alias nombra a su canónico: el grupo llega armado desde el backend, la pantalla no
  // lo deduce.
  const aliasRow = page.getByRole('row', { name: /DANDD/ })
  await expect(aliasRow).toBeVisible()
  await expect(aliasRow).toContainText('D&D 5e')

  await context.close()
})

/**
 * Las tres pestañas son el mismo catálogo con distinta tabla detrás, y el estado vive en la URL:
 * una fila de un catálogo es algo que un admin le pasa a otro (#164 en espíritu).
 */
test('the chosen catalog and the search live in the URL', async ({ browser }) => {
  const { context, page } = await newAuthenticatedPage(browser, `e2e-cat-url-${runId}`, true)

  await page.goto('/admin/catalogs')
  await page.getByRole('tab', { name: 'Plataformas' }).click()

  await expect(page).toHaveURL(/kind=platforms/)
  await expect(page.getByRole('row', { name: /Discord/ })).toBeVisible()

  // Recargar con la URL puesta tiene que dejar la pantalla igual, no volver a Sistemas.
  await page.reload()
  await expect(page.getByRole('tab', { name: 'Plataformas', selected: true })).toBeVisible()

  await context.close()
})

/**
 * #81 completo: dar de baja saca el valor de circulación sin romper vínculos, y restaurarlo lo
 * devuelve a como estaba. Se usa un tag suelto de la semilla —sin equivalentes— para que la baja no
 * tenga que elegir sucesor, que es el otro camino y tiene su propio test de integración.
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

  // Y vuelve: el valor sigue ahí para el admin, que es la diferencia entre dar de baja y borrar.
  await row.getByRole('button', { name: 'Restaurar' }).first().click()
  await expect(page.getByText('Se restauró «Homebrew»')).toBeVisible()
  await expect(row).toContainText('Aceptado')

  await context.close()
})
