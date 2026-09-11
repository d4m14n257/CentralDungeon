import { test, expect, type APIRequestContext } from '@playwright/test'

import { chooseRequiredCatalogs } from './helpers/tableWizard'

/**
 * Leaving work in progress asks first (decisiones.md #231).
 *
 * An e2e and not a component test because the thing being tested is a router transition being held
 * and then released - a `MemoryRouter` blocks nothing, and the bug this prevents only ever happened
 * to somebody clicking a real link.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

async function testLogin(request: APIRequestContext, discordId: string) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asMaster: true, asAdmin: false },
  })
  expect(response.ok()).toBeTruthy()
}

test('salir del wizard a medio llenar pregunta, y quedarse conserva lo escrito', async ({ browser }) => {
  const context = await browser.newContext()
  try {
    await testLogin(context.request, `e2e-unsaved-${runId}`)
    const page = await context.newPage()

    await page.goto('/master/tables/new')
    await page.getByRole('textbox', { name: 'Nombre' }).fill(`Mesa a medio llenar ${runId}`)
    await page.getByRole('button', { name: 'Siguiente' }).click()
    await chooseRequiredCatalogs(page)

    await page.getByRole('link', { name: 'Mis mesas' }).click()

    await expect(page.getByRole('heading', { name: '¿Salir sin guardar?' })).toBeVisible()

    await page.getByRole('button', { name: 'Seguir editando' }).click()

    // Still on the wizard, still on the step it was on, still holding what was picked.
    await expect(page).toHaveURL(/\/master\/tables\/new$/)
    await page.getByRole('button', { name: 'Atrás' }).click()
    await expect(page.getByRole('textbox', { name: 'Nombre' })).toHaveValue(`Mesa a medio llenar ${runId}`)
  } finally {
    await context.close()
  }
})

test('confirmar deja salir, y un wizard intacto no pregunta nada', async ({ browser }) => {
  const context = await browser.newContext()
  try {
    await testLogin(context.request, `e2e-unsaved-clean-${runId}`)
    const page = await context.newPage()

    // Nothing typed: leaving is not losing anything, and a question that always appears stops
    // being read.
    await page.goto('/master/tables/new')
    await page.getByRole('link', { name: 'Mis mesas' }).click()
    await expect(page).toHaveURL(/\/master\/tables$/)

    await page.goto('/master/tables/new')
    await page.getByRole('textbox', { name: 'Nombre' }).fill(`Mesa descartada ${runId}`)
    await page.getByRole('link', { name: 'Mis mesas' }).click()
    await page.getByRole('button', { name: 'Salir y perder lo hecho' }).click()

    await expect(page).toHaveURL(/\/master\/tables$/)
  } finally {
    await context.close()
  }
})
