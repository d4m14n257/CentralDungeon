import { test, expect, type APIRequestContext } from '@playwright/test'

/**
 * La ayuda (decisiones.md #167) y el camino que la enlaza desde el buscador. El link se prueba acá
 * y no en un test de componente porque lo que falló una vez fue la resolución de la ruta: con el
 * patrón relativo de `paths` terminaba en `/admin/tables/help`, y un MemoryRouter no lo delata.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

async function testLogin(request: APIRequestContext, discordId: string, asAdmin = false) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, { params: { discordId, asAdmin } })
  expect(response.ok()).toBeTruthy()
}

test('la ayuda se abre desde el buscador y explica cómo buscar', async ({ browser }) => {
  const context = await browser.newContext()
  try {
    await testLogin(context.request, `e2e-help-${runId}`, true)
    const page = await context.newPage()

    await page.goto('/admin/tables')
    await page.getByRole('button', { name: 'Crear mesa sin master' }).click()
    const createDialog = page.getByRole('dialog')
    await createDialog.getByRole('textbox', { name: 'Nombre' }).fill(`Mesa Ayuda E2E ${runId}`)
    await createDialog.getByRole('button', { name: 'Crear mesa sin master' }).click()

    const row = page.getByRole('listitem').filter({ hasText: `Mesa Ayuda E2E ${runId}` })
    await row.getByRole('button', { name: 'Asignar masters' }).click()
    await page.getByRole('dialog').getByRole('link', { name: 'Cómo buscar' }).click()

    await expect(page).toHaveURL(/\/help$/)
    await expect(page.getByRole('heading', { name: 'Cómo funciona CentralDungeon' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Cómo buscar' })).toBeVisible()
    await expect(page.getByText('/user_name damian,carlos,daniel')).toBeVisible()
  } finally {
    await context.close()
  }
})
