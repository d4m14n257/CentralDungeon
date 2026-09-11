import { test, expect, type APIRequestContext } from '@playwright/test'

/**
 * The context chip reports **where you are**, not what you hold (decisiones.md #222).
 *
 * It used to be checked inside the help spec, which was where the test actors with exactly one role
 * happened to be built. #231 removed that spec's reason to exist and this has nothing to do with the
 * help, so it lives on its own.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

async function testLogin(request: APIRequestContext, discordId: string, asMaster: boolean) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asMaster, asAdmin: false },
  })
  expect(response.ok()).toBeTruthy()
}

test('el chip dice el contexto de la URL, aunque no tengas ese rol', async ({ browser }) => {
  const context = await browser.newContext()
  try {
    // A master and nothing else, which is also what proves the roles do not stack on the way in.
    await testLogin(context.request, `e2e-chip-${runId}`, true)
    const page = await context.newPage()

    await page.goto('/player')
    await expect(page.getByRole('banner').getByText('Jugador', { exact: true })).toBeVisible()

    await page.goto('/master')
    await expect(page.getByRole('banner').getByText('Master', { exact: true })).toBeVisible()
  } finally {
    await context.close()
  }
})
