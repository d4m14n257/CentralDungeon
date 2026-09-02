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

  // Usuario nuevo: el callback lo manda a completar el onboarding, no al home.
  // CardTitle de shadcn es un div, no un heading: por texto, no por rol.
  await expect(page.getByText('Antes de empezar')).toBeVisible()
  await expect(page).toHaveURL(/\/onboarding$/)

  // Y la sesión es real: al completar el onboarding el backend acepta el access token.
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

  // El título va dentro de un chip de tono (CallbackCard), no en un heading: por texto, no por rol.
  await expect(page.getByText('Todavía no sos parte del servidor')).toBeVisible()
  // Que sea una invitación de Discord, no cuál: si backend/.env define DISCORD_INVITE_URL, esa
  // variable de entorno le gana a application-test.yml y el valor concreto depende de la máquina.
  await expect(page.getByRole('link', { name: 'Unirme al servidor' })).toHaveAttribute('href', /^https:\/\/discord\.gg\//)
})
