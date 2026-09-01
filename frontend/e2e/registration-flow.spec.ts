import { test, expect, type APIRequestContext, type Browser } from '@playwright/test'

/**
 * E1's vertical slice, end to end: a player finds an open table, applies, and the master
 * accepts them. Login goes through TestLoginController (backend "test" profile) instead of
 * a real Discord round trip - there is no Discord app registered yet (plan-desarrollo.md E1
 * Bloque 8). Table creation has no UI in E1's scope (frontend-diseno.md's sitemap subset for
 * this stage has no "create table" screen), so it is seeded directly against the API, the same
 * way a fixture would be in any e2e suite.
 *
 * Known limitation: there is no reset/seed harness yet, so each run leaves its user/table/
 * registration rows in the dev database - the random discordId suffix keeps repeated runs from
 * colliding with each other, not from accumulating data.
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

/**
 * Preparation -> Opened ya no es self-service del master (E2 sub-rebanada 1, decisiones.md #163):
 * un admin tiene que aprobarla. Se loguea un admin efímero solo para eso.
 */
async function createOpenTable(request: APIRequestContext, accessToken: string, name: string) {
  const created = await request.post(`${BACKEND_URL}/api/v1/game-tables`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: { name, maxPlayers: 4 },
  })
  expect(created.ok()).toBeTruthy()
  const table = (await created.json()) as { id: string }

  const admin = await testLogin(request, `e2e-admin-${Math.random().toString(36).slice(2, 10)}`, false, true)
  const approved = await request.post(`${BACKEND_URL}/api/v1/game-tables/${table.id}/approve`, {
    headers: { Authorization: `Bearer ${admin.accessToken}` },
  })
  expect(approved.ok()).toBeTruthy()
  return table.id
}

async function newAuthenticatedPage(browser: Browser, discordId: string, asMaster: boolean) {
  const context = await browser.newContext()
  await testLogin(context.request, discordId, asMaster)
  const page = await context.newPage()
  return { context, page }
}

test('a player applies to an open table and the master accepts them', async ({ browser, request }) => {
  const masterDiscordId = `e2e-master-${runId}`
  const playerDiscordId = `e2e-player-${runId}`
  const tableName = `Mesa E2E ${runId}`

  const master = await testLogin(request, masterDiscordId, true)
  const tableId = await createOpenTable(request, master.accessToken, tableName)

  const player = await newAuthenticatedPage(browser, playerDiscordId, false)
  try {
    await player.page.goto('/')
    const tableCard = player.page.getByRole('link', { name: new RegExp(tableName) })
    await expect(tableCard).toBeVisible()
    await tableCard.click()

    await expect(player.page.getByRole('heading', { name: tableName })).toBeVisible()
    await player.page.getByRole('button', { name: 'Postularme' }).click()
    await player.page.getByRole('textbox', { name: 'Mensaje para el master' }).fill('Quiero sumarme a esta mesa')
    await player.page.getByRole('dialog').getByRole('button', { name: 'Postularme' }).click()

    await expect(player.page.getByRole('button', { name: 'Ya tenés una postulación en curso' })).toBeVisible()

    const master2 = await newAuthenticatedPage(browser, masterDiscordId, true)
    try {
      await master2.page.goto(`/master/tables/${tableId}`)
      await expect(master2.page.getByText(new RegExp(`1\\. .* · 8000`))).toBeVisible()

      await master2.page.getByRole('button', { name: 'Aceptar' }).click()
      await master2.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()

      await expect(master2.page.getByText('No hay candidatos esperando')).toBeVisible()
    } finally {
      await master2.context.close()
    }

    await player.page.goto('/my/tables')
    await expect(player.page.getByRole('link', { name: new RegExp(tableName) })).toBeVisible()

    await player.page.goto('/notifications')
    await expect(player.page.getByText(new RegExp(`Te aceptaron en ${tableName}`))).toBeVisible()
  } finally {
    await player.context.close()
  }
})
