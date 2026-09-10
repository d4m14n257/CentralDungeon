import { expect, test, type APIRequestContext, type Browser } from '@playwright/test'

import { addScheduleSlot, chooseRequiredCatalogs } from './helpers/tableWizard'

const BACKEND_URL = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

async function testLogin(request: APIRequestContext, discordId: string, asMaster = false) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, { params: { discordId, asMaster } })
  expect(response.ok()).toBeTruthy()
}

async function newAuthenticatedPage(browser: Browser, discordId: string, asMaster: boolean) {
  const context = await browser.newContext()
  await testLogin(context.request, discordId, asMaster)
  const page = await context.newPage()
  return { context, page }
}

/**
 * The reader's own week (#227): the screen exists so that finding room for a table is looking rather
 * than guessing, and so the grid it is found on is the same one the wizard offers to claim from.
 */
test('a master sees the week they have committed, and takes a free hour from the grid', async ({ browser }) => {
  const { context, page } = await newAuthenticatedPage(browser, `e2e-week-${runId}`, true)
  const firstName = `Mesa Semana A E2E ${runId}`

  try {
    // A table with a real agenda, so the week has something in it.
    await page.goto('/master/tables/new')
    await page.getByRole('textbox', { name: 'Nombre' }).fill(firstName)
    await page.getByRole('button', { name: 'Siguiente' }).click()
    await chooseRequiredCatalogs(page)
    await page.getByRole('button', { name: 'Siguiente' }).click()
    await addScheduleSlot(page, '20:00', 'Martes')
    await page.getByRole('button', { name: 'Siguiente' }).click()
    // The files step (#228): nothing is required there.
    await page.getByRole('button', { name: 'Siguiente' }).click()
    await page.getByRole('button', { name: 'Crear mesa' }).click()
    await expect(page.getByRole('heading', { name: firstName })).toBeVisible()

    // It shows on the person's own week, which belongs to no context (#222).
    await page.goto('/my/schedule')
    await expect(page.getByRole('heading', { name: 'Mi horario' })).toBeVisible()
    // The list underneath says when, for the blocks too small to read their own label.
    await expect(page.getByText('Mar 20:00–23:00')).toBeVisible()

    // And the block itself is a shortcut: looking at the week and going to one of its tables is one
    // errand, not two (#227).
    await page
      .getByRole('link', { name: `${firstName} — la dirigís` })
      .first()
      .click()
    await expect(page).toHaveURL(/\/master\/tables\/[0-9a-f-]+$/)

    // And the same grid, inside the wizard, is what the next table is built from: the hour the first
    // table took is no longer offered, and a free one can be claimed with a click.
    await page.goto('/master/tables/new')
    await page.getByRole('textbox', { name: 'Nombre' }).fill(`Mesa Semana B E2E ${runId}`)
    await page.getByRole('button', { name: 'Siguiente' }).click()
    await chooseRequiredCatalogs(page)
    await page.getByRole('button', { name: 'Siguiente' }).click()

    await expect(page.getByRole('button', { name: 'Ocupar Martes 20:00' })).toBeHidden()
    // In the wizard the blocks lead nowhere on purpose: navigating away would throw away the form.
    await expect(page.getByRole('link', { name: `${firstName} — la dirigís` })).toHaveCount(0)
    await page.getByRole('button', { name: 'Ocupar Jueves 21:00' }).click()
    // Claiming it fills the agenda, which is what lets the step be left at all (#226).
    await page.getByRole('button', { name: 'Siguiente' }).click()
    await expect(page.getByText('Lo que le das a la mesa', { exact: false })).toBeVisible()
    await page.getByRole('button', { name: 'Siguiente' }).click()
    await expect(page.getByRole('heading', { name: 'Revisión' })).toBeVisible()
  } finally {
    await context.close()
  }
})
