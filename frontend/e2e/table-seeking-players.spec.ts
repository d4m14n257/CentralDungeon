import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

import { applyToTable } from './helpers/application'
import { runnableTableBody } from './helpers/tableApi'

/**
 * Seeking players on a running table, end to end against the real backend.
 *
 * A table in progress with a free seat can open a call for players: it appears in the explorer with a
 * "seeking players" chip, a newcomer applies, the master accepts, and when that acceptance fills the
 * table the call turns itself off - so the explorer stops offering a table with no seat left, with
 * nobody having to remember to close it.
 *
 * Three things only a run like this can see: that the explorer (a different endpoint, a different
 * query) honours the flag; that an application to a table already in progress is accepted while the
 * call is open; and that the automatic switch-off crosses from the registration service back into the
 * table's own state, which the master reads on the status notice.
 *
 * Login through TestLoginController (the backend's `test` profile), like every other spec.
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

async function newAuthenticatedPage(browser: Browser, discordId: string, asMaster = false) {
  const context = await browser.newContext()
  await testLogin(context.request, discordId, asMaster)
  return { context, page: await context.newPage() }
}

/** The master presses a confirm in the dialog that a decision opens. */
async function confirmDialog(page: Page) {
  await page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
}

/**
 * A person applies and the master accepts them, from the master's own page.
 *
 * @param master     the master's page
 * @param player     the applicant's page
 * @param tableId    the table
 * @param playerName the applicant's handle, which is how the candidate is found
 */
async function applyAndAccept(master: Page, player: Page, tableId: string, playerName: string) {
  await player.goto(`/player/tables/${tableId}`)
  await applyToTable(player)

  await master.goto(`/master/tables/${tableId}`)
  await master.getByRole('listitem').filter({ hasText: playerName }).first().getByRole('button', { name: 'Aceptar' }).click()
  await confirmDialog(master)
}

test('a running table turns the search on, takes the applicant that fills it, and the search turns itself off', async ({
  browser,
  request,
}) => {
  const tableName = `Mesa Busca Jugadores E2E ${runId}`
  const masterDiscordId = `e2e-seek-master-${runId}`
  const seatedDiscordId = `e2e-seek-seated-${runId}`
  const newcomerDiscordId = `e2e-seek-newcomer-${runId}`

  // Two seats: one taken to start the table, one left for the search to fill.
  const masterToken = (await testLogin(request, masterDiscordId, true)).accessToken
  const created = await request.post(`${BACKEND_URL}/api/v1/game-tables`, {
    headers: { Authorization: `Bearer ${masterToken}` },
    data: runnableTableBody(tableName, { maxPlayers: 2 }),
  })
  expect(created.ok()).toBeTruthy()
  const tableId = ((await created.json()) as { id: string }).id
  expect(
    (
      await request.post(`${BACKEND_URL}/api/v1/game-tables/${tableId}/submit`, { headers: { Authorization: `Bearer ${masterToken}` } })
    ).ok(),
  ).toBeTruthy()
  const adminToken = (await testLogin(request, `e2e-seek-admin-${runId}`, false, true)).accessToken
  expect(
    (
      await request.post(`${BACKEND_URL}/api/v1/game-tables/${tableId}/approve`, { headers: { Authorization: `Bearer ${adminToken}` } })
    ).ok(),
  ).toBeTruthy()

  const master = await newAuthenticatedPage(browser, masterDiscordId, true)
  const seated = await newAuthenticatedPage(browser, seatedDiscordId)
  const newcomer = await newAuthenticatedPage(browser, newcomerDiscordId)
  const bystander = await newAuthenticatedPage(browser, `e2e-seek-bystander-${runId}`)

  try {
    await applyAndAccept(master.page, seated.page, tableId, seatedDiscordId)

    // Running, with one seat free and no call open yet.
    await master.page.goto(`/master/tables/${tableId}`)
    await master.page.getByRole('button', { name: 'Iniciar mesa' }).click()
    await confirmDialog(master.page)
    await expect(master.page.getByText('En curso', { exact: true })).toBeVisible()

    // Before the call, a running table is not on offer: the explorer is for tables that want people.
    await bystander.page.goto('/player')
    await expect(bystander.page.getByRole('heading', { name: tableName })).toBeHidden()

    // ---------------------------------------------------------------------------- the call opens

    await master.page.getByRole('button', { name: 'Buscar jugadores' }).click()
    await confirmDialog(master.page)
    await expect(master.page.getByRole('button', { name: 'Dejar de buscar' })).toBeVisible()
    await expect(master.page.getByText(/aparece en el explorador/)).toBeVisible()

    await bystander.page.goto(`/player?q=${encodeURIComponent(tableName)}`)
    const card = bystander.page.getByRole('link', { name: new RegExp(tableName) })
    await expect(card).toBeVisible()
    await expect(card).toContainText('Busca jugadores')

    // ------------------------------------------------------------ the newcomer is accepted, table full

    await applyAndAccept(master.page, newcomer.page, tableId, newcomerDiscordId)

    // The seat that was left is gone, so the call closed by itself: the button is back to offering to
    // open it - disabled, with the reason said - rather than to close it.
    await master.page.goto(`/master/tables/${tableId}`)
    await expect(master.page.getByRole('button', { name: 'Buscar jugadores' })).toBeDisabled()
    await expect(master.page.getByRole('button', { name: 'Dejar de buscar' })).toHaveCount(0)
    await expect(master.page.getByText('La mesa está completa')).toBeVisible()

    // And the explorer no longer offers a table with no seat.
    await bystander.page.goto(`/player?q=${encodeURIComponent(tableName)}`)
    await expect(bystander.page.getByRole('link', { name: new RegExp(tableName) })).toBeHidden()
  } finally {
    await bystander.context.close()
    await newcomer.context.close()
    await seated.context.close()
    await master.context.close()
  }
})
