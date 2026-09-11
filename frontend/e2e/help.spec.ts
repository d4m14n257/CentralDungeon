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

  const row = page.getByRole('listitem').filter({ hasText: tableName })
  await row.getByRole('button', { name: 'Asignar masters' }).click()
  return page.getByRole('dialog')
}

test('la ayuda se lee sin salir de la pantalla, y la pantalla sigue ahí detrás', async ({ browser }) => {
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

test('la ayuda enseña con pasos, no solo describe', async ({ browser }) => {
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

test('/help ya no es una pantalla: queda libre para soporte', async ({ browser }) => {
  const context = await browser.newContext()
  try {
    await testLogin(context.request, `e2e-help-gone-${runId}`, { asMaster: true })
    const page = await context.newPage()

    await page.goto('/help')

    // Not a redirect and not an empty page: nothing claims the path until the support screen does
    // (#231). A route that resolved to nothing would be the dead end E1 already documented.
    await expect(page.getByRole('heading', { name: '404' })).toBeVisible()
  } finally {
    await context.close()
  }
})
