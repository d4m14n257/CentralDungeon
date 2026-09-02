import { test, expect, type APIRequestContext, type Page } from '@playwright/test'

/**
 * La ayuda (decisiones.md #167) partida por audiencia y enlazada por `#ref` (#168). Se prueba acá
 * y no en tests de componente porque lo que puede romperse es la resolución de rutas: con el
 * patrón relativo de `paths` el link terminaba en `/admin/tables/help`, y un MemoryRouter no lo
 * delata.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

async function testLogin(request: APIRequestContext, discordId: string, asAdmin = false) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, { params: { discordId, asAdmin } })
  expect(response.ok()).toBeTruthy()
}

async function openAssignMastersDialog(page: Page) {
  await page.goto('/admin/tables')
  await page.getByRole('button', { name: 'Crear mesa sin master' }).click()
  const createDialog = page.getByRole('dialog')
  await createDialog.getByRole('textbox', { name: 'Nombre' }).fill(`Mesa Ayuda E2E ${runId}`)
  await createDialog.getByRole('button', { name: 'Crear mesa sin master' }).click()

  const row = page.getByRole('listitem').filter({ hasText: `Mesa Ayuda E2E ${runId}` })
  await row.getByRole('button', { name: 'Asignar masters' }).click()
  return page.getByRole('dialog')
}

test('el buscador y el diálogo enlazan a la sección de ayuda que corresponde', async ({ browser }) => {
  const context = await browser.newContext()
  try {
    await testLogin(context.request, `e2e-help-${runId}`, true)
    const page = await context.newPage()

    const dialog = await openAssignMastersDialog(page)
    await dialog.getByRole('link', { name: 'Cómo funciona' }).click()

    // Un #ref de otra audiencia: la ayuda de asignar masters vive en la de admins.
    await expect(page).toHaveURL(/\/help\/admins#assign-masters$/)
    await expect(page.getByRole('heading', { name: 'Mesas sin master' })).toBeVisible()

    const searchDialog = await openAssignMastersDialog(page)
    await searchDialog.getByRole('link', { name: 'Cómo buscar' }).click()

    await expect(page).toHaveURL(/\/help#search$/)
    await expect(page.getByRole('heading', { name: 'Cómo buscar' })).toBeVisible()
    await expect(page.getByText('/user_name damian,carlos,daniel')).toBeVisible()
  } finally {
    await context.close()
  }
})

test('la ayuda se recorre por audiencia, cada una con su URL', async ({ browser }) => {
  const context = await browser.newContext()
  try {
    await testLogin(context.request, `e2e-help-nav-${runId}`)
    const page = await context.newPage()

    await page.goto('/help')
    await expect(page.getByRole('heading', { name: 'Los contextos' })).toBeVisible()

    await page.getByRole('link', { name: 'Jugadores' }).click()
    await expect(page).toHaveURL(/\/help\/players$/)
    await expect(page.getByRole('heading', { name: 'Postularte a una mesa' })).toBeVisible()

    await page.getByRole('link', { name: 'Masters' }).click()
    await expect(page).toHaveURL(/\/help\/masters$/)
    await expect(page.getByRole('heading', { name: 'Crear una mesa' })).toBeVisible()

    // La ayuda de un rol no arrastra la de otro: cada audiencia muestra solo lo suyo.
    await expect(page.getByRole('heading', { name: 'Postularte a una mesa' })).toBeHidden()
  } finally {
    await context.close()
  }
})
