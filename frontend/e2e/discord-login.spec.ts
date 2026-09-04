import { test, expect, type APIRequestContext } from '@playwright/test'

/**
 * The front door, end to end: the login button, the OAuth2 handshake, the guild check, the refresh
 * cookie and the callback screen deciding where to drop the user. Discord is the stub the backend
 * serves under the "test" profile (auth/TestDiscordController), so this runs the real
 * DiscordOAuth2UserService - the same code path a real login takes, minus discord.com.
 *
 * Backend must be up with `dev,test` (backend/README.md). Who logs in is server state, so
 * nextDiscordLogin() is called before each navigation - the login link carries no parameters.
 *
 * Known limitation, same as registration-flow.spec.ts: no reset harness yet, so each run leaves
 * its user row behind; the random suffix only keeps runs from colliding with each other.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

async function nextDiscordLogin(request: APIRequestContext, discordId: string, username: string, inGuild: boolean) {
  const response = await request.post(`${BACKEND_URL}/test-discord/next-login`, {
    params: { discordId, username, inGuild },
  })
  expect(response.ok()).toBeTruthy()
}

test('un miembro del servidor entra con Discord y queda logueado', async ({ page, request }) => {
  await nextDiscordLogin(request, `e2e-discord-${runId}`, `Vecna ${runId}`, true)

  await page.goto('/login')
  await page.getByRole('link', { name: 'Entrar con Discord' }).click()

  // A new user: the callback sends them to complete the onboarding, not to the home.
  // CardTitle de shadcn es un div, no un heading: por texto, no por rol.
  await expect(page.getByText('Antes de empezar')).toBeVisible()
  await expect(page).toHaveURL(/\/onboarding$/)

  // And the session is real: on completing the onboarding the backend accepts the access token.
  await page.getByLabel('Nombre a mostrar').fill(`Vecna ${runId}`)
  await page.getByRole('combobox', { name: 'País' }).click()
  await page.getByRole('option', { name: 'Argentina' }).click()
  await page.getByRole('button', { name: 'Continuar' }).click()

  await expect(page).toHaveURL(/\/$/)
})

test('quien no está en el servidor no entra y recibe la invitación', async ({ page, request }) => {
  await nextDiscordLogin(request, `e2e-outsider-${runId}`, `Randolph ${runId}`, false)

  await page.goto('/login')
  await page.getByRole('link', { name: 'Entrar con Discord' }).click()

  // The title sits inside a toned chip (CallbackCard) rather than a heading: matched by text, not by role.
  await expect(page.getByText('Todavía no sos parte del servidor')).toBeVisible()
  // That it is a Discord invitation, not which one: if backend/.env defines DISCORD_INVITE_URL, that
  // environment variable beats application-test.yml and the concrete value depends on the machine.
  await expect(page.getByRole('link', { name: 'Unirme al servidor' })).toHaveAttribute('href', /^https:\/\/discord\.gg\//)
})
