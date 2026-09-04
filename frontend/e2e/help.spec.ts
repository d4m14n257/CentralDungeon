import { test, expect, type APIRequestContext, type Page } from '@playwright/test'

/**
 * The help (decisiones.md #167) split by audience (#168), linked by `#ref` and **tied to the
 * reader's role** (#169, #170). It is tested here rather than in component tests because what can
 * break is route resolution: with the relative pattern in `paths` the link ended at
 * `/admin/tables/help`, and a MemoryRouter does not give that away.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

async function testLogin(request: APIRequestContext, discordId: string, roles: { asMaster?: boolean; asAdmin?: boolean } = {}) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asMaster: roles.asMaster ?? false, asAdmin: roles.asAdmin ?? false },
  })
  expect(response.ok()).toBeTruthy()
}

/** The name carries a `label` because one test opens the dialog twice: two tables, two names. */
async function openAssignMastersDialog(page: Page, label: string) {
  const tableName = `Mesa Ayuda E2E ${runId} ${label}`
  await page.goto('/admin/tables')
  await page.getByRole('button', { name: 'Crear mesa sin master' }).click()
  const createDialog = page.getByRole('dialog')
  await createDialog.getByRole('textbox', { name: 'Nombre' }).fill(tableName)
  await createDialog.getByRole('button', { name: 'Crear mesa sin master' }).click()

  const row = page.getByRole('listitem').filter({ hasText: tableName })
  await row.getByRole('button', { name: 'Asignar masters' }).click()
  return page.getByRole('dialog')
}

test('el buscador y el diálogo enlazan a la sección de ayuda que corresponde, y la resaltan', async ({ browser }) => {
  const context = await browser.newContext()
  try {
    await testLogin(context.request, `e2e-help-${runId}`, { asAdmin: true })
    const page = await context.newPage()

    const dialog = await openAssignMastersDialog(page, 'ref')
    await dialog.getByRole('link', { name: 'Cómo funciona' }).click()

    // A #ref from another audience: the help for assigning masters lives in the admins' page.
    await expect(page).toHaveURL(/\/help\/admins#assign-masters$/)
    await expect(page.getByRole('heading', { name: 'Mesas sin master' })).toBeVisible()
    // The section the URL names is marked, so the reader can tell which one they came for (#170).
    await expect(page.locator('section#assign-masters')).toHaveAttribute('aria-current', 'location')
    await expect(page.locator('section#reviewing')).not.toHaveAttribute('aria-current', 'location')

    const searchDialog = await openAssignMastersDialog(page, 'buscador')
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
    // The steps are an ordered list: that is what separates "teaching how to use it" from "describing it" (#170).
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

    // Reaching another role's help by URL does not show it either.
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
