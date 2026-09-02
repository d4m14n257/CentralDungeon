import { test, expect, type APIRequestContext, type Page } from '@playwright/test'

/**
 * La ayuda (decisiones.md #167) partida por audiencia (#168), enlazada por `#ref` y **atada al rol
 * de quien lee** (#169, #170). Se prueba acá y no en tests de componente porque lo que puede
 * romperse es la resolución de rutas: con el patrón relativo de `paths` el link terminaba en
 * `/admin/tables/help`, y un MemoryRouter no lo delata.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

async function testLogin(request: APIRequestContext, discordId: string, roles: { asMaster?: boolean; asAdmin?: boolean } = {}) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asMaster: roles.asMaster ?? false, asAdmin: roles.asAdmin ?? false },
  })
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

test('el buscador y el diálogo enlazan a la sección de ayuda que corresponde, y la resaltan', async ({ browser }) => {
  const context = await browser.newContext()
  try {
    await testLogin(context.request, `e2e-help-${runId}`, { asAdmin: true })
    const page = await context.newPage()

    const dialog = await openAssignMastersDialog(page)
    await dialog.getByRole('link', { name: 'Cómo funciona' }).click()

    // Un #ref de otra audiencia: la ayuda de asignar masters vive en la de admins.
    await expect(page).toHaveURL(/\/help\/admins#assign-masters$/)
    await expect(page.getByRole('heading', { name: 'Mesas sin master' })).toBeVisible()
    // La sección que la URL nombra queda marcada, para saber cuál se vino a leer (#170).
    await expect(page.locator('section#assign-masters')).toHaveAttribute('aria-current', 'location')
    await expect(page.locator('section#reviewing')).not.toHaveAttribute('aria-current', 'location')

    const searchDialog = await openAssignMastersDialog(page)
    await searchDialog.getByRole('link', { name: 'Cómo buscar' }).click()

    await expect(page).toHaveURL(/\/help#search$/)
    await expect(page.getByRole('heading', { name: 'Cómo buscar' })).toBeVisible()
    await expect(page.getByText('/user_name damian,carlos,daniel')).toBeVisible()
  } finally {
    await context.close()
  }
})

test('la ayuda enseña con pasos, no solo describe', async ({ browser }) => {
  const context = await browser.newContext()
  try {
    await testLogin(context.request, `e2e-help-steps-${runId}`, { asMaster: true })
    const page = await context.newPage()

    await page.goto('/help/masters')
    // Los pasos son una lista ordenada: es lo que separa "enseñar a usarlo" de "describirlo" (#170).
    const steps = page.locator('section#creating ol > li')
    await expect(page.locator('section#creating').getByRole('heading', { name: 'Cómo se hace' })).toBeVisible()
    await expect(steps).toHaveCount(5)
    await expect(steps.first()).toContainText('Cambiá al contexto Master')
  } finally {
    await context.close()
  }
})

test('cada quien ve la ayuda de su rol, y solo la de su rol', async ({ browser }) => {
  const player = await browser.newContext()
  try {
    await testLogin(player.request, `e2e-help-player-${runId}`)
    const page = await player.newPage()

    await page.goto('/help')
    await expect(page.getByRole('link', { name: 'Jugadores' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Masters' })).toBeHidden()
    await expect(page.getByRole('link', { name: 'Admins' })).toBeHidden()

    await page.getByRole('link', { name: 'Jugadores' }).click()
    await expect(page).toHaveURL(/\/help\/players$/)
    await expect(page.getByRole('heading', { name: 'Postularte a una mesa' })).toBeVisible()

    // Entrar por URL a la ayuda de otro rol tampoco la muestra.
    await page.goto('/help/admins')
    await expect(page.getByText('No tenés permiso')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Mesas sin master' })).toBeHidden()
  } finally {
    await player.close()
  }

  const admin = await browser.newContext()
  try {
    await testLogin(admin.request, `e2e-help-admin-${runId}`, { asAdmin: true })
    const page = await admin.newPage()

    await page.goto('/help/admins')
    await expect(page.getByRole('heading', { name: 'Mesas sin master' })).toBeVisible()
  } finally {
    await admin.close()
  }
})
