import { test, expect, type APIRequestContext, type Page } from '@playwright/test'

/**
 * The help as dialogs raised in place (decisiones.md #231), which replaced the `/help` routes of
 * #167 and #168.
 *
 * It is tested here and not in a component test because what the change is *for* only exists in a
 * real browser: that asking a question does not cost the screen you asked it from. A MemoryRouter
 * cannot tell you whether the page behind the dialog survived.
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

  // `/admin/tables` is one of the wide tables of frontend-diseno.md §5.b since F3.3: a `<tr>`.
  const row = page.getByRole('row').filter({ hasText: tableName })
  await row.getByRole('button', { name: 'Asignar masters' }).click()
  return page.getByRole('dialog')
}

test('the help is read without leaving the screen, and the screen is still there behind it', async ({ browser }) => {
  const context = await browser.newContext()
  try {
    await testLogin(context.request, `e2e-help-${runId}`, { asAdmin: true })
    const page = await context.newPage()

    const dialog = await openAssignMastersDialog(page, 'modal')
    await dialog.getByRole('button', { name: 'Cómo funciona' }).click()

    // The help for assigning masters, on top of the dialog that asked for it - and the URL did not
    // move, which is the whole point (#231).
    await expect(page.getByRole('heading', { name: 'Mesas sin master' })).toBeVisible()
    await expect(page).toHaveURL(/\/admin\/tables$/)

    await page.getByRole('button', { name: 'Close' }).first().click()
    // The dialog underneath is still open with what was typed in it.
    await expect(page.getByRole('heading', { name: 'Asignar masters' })).toBeVisible()
  } finally {
    await context.close()
  }
})

test('the help teaches with steps rather than only describing', async ({ browser }) => {
  const context = await browser.newContext()
  try {
    await testLogin(context.request, `e2e-help-steps-${runId}`, { asMaster: true })
    const page = await context.newPage()

    await page.goto('/master')
    await page.getByRole('button', { name: 'Cómo se lee esta lista' }).click()

    const helpDialog = page.getByRole('dialog')
    // The steps are an ordered list: that is what separates "teaching how to use it" from
    // "describing it" (#170, kept by #231).
    await expect(helpDialog.getByRole('heading', { name: 'Cómo se hace' })).toBeVisible()
    await expect(helpDialog.locator('ol > li')).toHaveCount(3)
  } finally {
    await context.close()
  }
})

test('/help is no longer the help: it is the support screen the reservation described', async ({ browser }) => {
  const context = await browser.newContext()
  try {
    await testLogin(context.request, `e2e-help-gone-${runId}`, { asMaster: true })
    const page = await context.newPage()

    await page.goto('/help')

    // **Hasta F3.2 esto era un 404 a propósito**: #231 sacó la pantalla de ayuda y dejó la ruta
    // reservada con todas las letras para «pedir asistencia, reportar un bug», sin nada detrás. El
    // pedido `General` de #42 es ese detrás, así que la reserva se cobró y la pantalla existe.
    await expect(page.getByRole('heading', { name: 'Ayuda' })).toBeVisible()
    // Y lo que hace es pedir, no volver a ser un índice: las explicaciones siguen siendo diálogos en
    // la pantalla que las provoca (#231), y eso lo dice en voz alta en vez de dejar creer que
    // la ayuda se perdió.
    await expect(page.getByRole('button', { name: 'Escribirle a un admin' })).toBeVisible()
    await expect(page.getByText('Las explicaciones están en cada pantalla')).toBeVisible()
  } finally {
    await context.close()
  }
})
