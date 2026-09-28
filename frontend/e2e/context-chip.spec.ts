import { test, expect, type APIRequestContext } from '@playwright/test'

/**
 * The context chip reports **where you are**, not what you hold (decisiones.md #222) - and since
 * #269, where you are is always a context you hold: forcing another one sends you home.
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

test('a context the account does not have sends it home, and the chip names where it landed', async ({ browser }) => {
  const context = await browser.newContext()
  try {
    // A master and nothing else, which is also what proves the roles do not stack on the way in.
    await testLogin(context.request, `e2e-chip-${runId}`, true)
    const page = await context.newPage()

    // #269: without the Player role, /player is not a door - the guard sends the account back to its
    // own home instead of painting the player shell (this used to assert the opposite, #222).
    await page.goto('/player')
    await expect(page).toHaveURL(/\/master$/)
    await expect(page.getByRole('banner').getByText('Master', { exact: true })).toBeVisible()

    await page.goto('/admin/users')
    await expect(page).toHaveURL(/\/master$/)

    await page.goto('/master')
    await expect(page.getByRole('banner').getByText('Master', { exact: true })).toBeVisible()
  } finally {
    await context.close()
  }
})
