import { test, expect, type APIRequestContext, type Browser } from '@playwright/test'

/**
 * E2 sub-rebanada 1, de punta a punta (decisiones.md #163): un master crea una mesa por el
 * wizard -> Preparation; un admin la aprueba desde /admin/tables -> Opened; el Primary la inicia
 * -> InProgress; el Primary la finaliza -> Finished. Cubre la máquina de estados real, no el
 * self-service que E1 tenía.
 *
 * Login por TestLoginController (backend "test" profile), igual que registration-flow.spec.ts -
 * no hay Discord real todavía.
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

test('a master creates a table, an admin approves it, and the master runs it end to end', async ({ browser }) => {
  const masterDiscordId = `e2e-master-${runId}`
  const adminDiscordId = `e2e-admin-${runId}`
  const tableName = `Mesa Ciclo E2E ${runId}`

  const master = await newAuthenticatedPage(browser, masterDiscordId, true, false)
  try {
    await master.page.goto('/master/tables/new')
    await master.page.getByRole('textbox', { name: 'Nombre' }).fill(tableName)
    await master.page.getByRole('button', { name: 'Crear mesa' }).click()

    await expect(master.page.getByRole('heading', { name: tableName })).toBeVisible()
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
      // /admin/tables solo lista lo que espera revisión (Unassigned/Preparation/ChangesRequested):
      // una vez Opened, la fila desaparece de esta bandeja en vez de quedar con el badge actualizado.
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
