import { test, expect, type Browser } from '@playwright/test'

/**
 * A session that was open when the account got blocked: the person has to land on the screen that
 * says so, never on a login that would tell them nothing - whether they come back by reloading or
 * the block catches them in the middle of navigating.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

async function signIn(request: import('@playwright/test').APIRequestContext, discordId: string, asAdmin = false) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asMaster: false, asAdmin, asOwner: false },
  })
  expect(response.ok()).toBeTruthy()
  return ((await response.json()) as { accessToken: string }).accessToken
}

/** A signed-in player on their home, and the call that makes an admin block them. */
async function signedInPlayerWhoGetsBlocked(browser: Browser, label: string) {
  const player = await browser.newContext()
  const playerToken = await signIn(player.request, `e2e-blk-${label}-p-${runId}`)
  const page = await player.newPage()
  await page.goto('/player')
  await expect(page).toHaveURL('/player')

  const admin = await browser.newContext()
  const adminHeaders = { Authorization: `Bearer ${await signIn(admin.request, `e2e-blk-${label}-a-${runId}`, true)}` }
  const me = await admin.request.get(`${BACKEND_URL}/api/v1/users/me`, { headers: { Authorization: `Bearer ${playerToken}` } })
  const { id } = (await me.json()) as { id: string }

  const block = () =>
    admin.request.post(`${BACKEND_URL}/api/v1/admin/users/${id}/block`, {
      headers: adminHeaders,
      data: { justification: 'E2E: block with the session open' },
    })
  return { player, page, admin, block }
}

test('a player blocked with the session open sees the blocked screen on reload, not the login', async ({ browser }) => {
  const { player, page, admin, block } = await signedInPlayerWhoGetsBlocked(browser, 'reload')
  try {
    expect((await block()).ok()).toBeTruthy()
    await page.reload()
    await expect(page).toHaveURL(/\/auth\/callback\?error=user_blocked/)
    await expect(page.getByText('Tu cuenta está bloqueada')).toBeVisible()
    await expect(page).not.toHaveURL(/\/login/)
  } finally {
    await player.close()
    await admin.close()
  }
})

test('a player blocked in the middle of navigating sees the blocked screen, not the login', async ({ browser }) => {
  const { player, page, admin, block } = await signedInPlayerWhoGetsBlocked(browser, 'nav')
  try {
    expect((await block()).ok()).toBeTruthy()
    // In-app navigation, no reload: the screen's own request is the first thing the block catches.
    await page.getByRole('link', { name: 'Mis postulaciones' }).first().click()
    await expect(page).toHaveURL(/\/auth\/callback\?error=user_blocked/)
    await expect(page.getByText('Tu cuenta está bloqueada')).toBeVisible()
    await expect(page).not.toHaveURL(/\/login/)
  } finally {
    await player.close()
    await admin.close()
  }
})

test('a session that ends for another reason still goes to the login', async ({ browser }) => {
  const player = await browser.newContext()
  try {
    await signIn(player.request, `e2e-blk-ctl-p-${runId}`)
    const page = await player.newPage()
    await page.goto('/player')
    await expect(page).toHaveURL('/player')

    await player.clearCookies()
    await page.reload()
    await expect(page).toHaveURL(/\/login/)
    await expect(page.getByText('Tu cuenta está bloqueada')).toHaveCount(0)
  } finally {
    await player.close()
  }
})
