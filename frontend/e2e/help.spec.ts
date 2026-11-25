import { test, expect, type APIRequestContext, type Page } from '@playwright/test'
import { acceptReview } from './helpers/review'

/**
 * The help as dialogs raised in place, which replaced the dedicated `/help` routes.
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
  await acceptReview(page)
  // The listing is paged oldest first, so a new table can land past the first page: search for it.
  await page.goto(`/admin/tables?q=${encodeURIComponent(tableName)}`)

  // `/admin/tables` is one of the wide tables, so its rows are a `<tr>`.
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
    // move, which is the whole point.
    await expect(page.getByRole('heading', { name: 'Mesas sin master' })).toBeVisible()
    await expect(page).toHaveURL(/\/admin\/tables\?q=[^/]*$/)

    await page.getByRole('button', { name: 'Cerrar' }).first().click()
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
    await page.getByRole('button', { name: 'Cómo funciona' }).click()

    const helpDialog = page.getByRole('dialog')
    // The steps are an ordered list: that is what separates "teaching how to use it" from
    // "describing it".
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

    // **This used to be a 404 on purpose**: the help screen was taken away and the route was left
    // reserved for asking for assistance or reporting a bug, with nothing behind it. The
    // `General` request is that something, so the reservation was cashed in and the screen exists.
    await expect(page.getByRole('heading', { name: 'Ayuda' })).toBeVisible()
    // And what it does is ask, not go back to being an index: the explanations are still dialogs on
    // the screen that provokes them, and it says so out loud rather than letting the reader
    // believe the help was lost.
    await expect(page.getByRole('button', { name: 'Escribirle a un admin' })).toBeVisible()
    await expect(page.getByText('Las explicaciones están en cada pantalla')).toBeVisible()
  } finally {
    await context.close()
  }
})
