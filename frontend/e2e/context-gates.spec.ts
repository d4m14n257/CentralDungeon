import { test, expect, request as playwrightRequest, type APIRequestContext, type Browser } from '@playwright/test'

import { runnableTableBody } from './helpers/tableApi'

/**
 * The two halves of the contexts, pinned together.
 *
 * The interface closes a context to an account that does not have it (a redirect to `/`, never a
 * "no permission" screen), and the backend does not depend on that: where a permission is a role it
 * answers 403 to whoever lacks it, whatever the interface showed. Each half alone could rot while the
 * other keeps the suite green, so both are asserted per actor.
 *
 * Actors come from the test login, which leaves an account holding exactly the roles asked for:
 * - player: Player only
 * - master: Master only (no Player, so it has no player context)
 * - admin / owner: Admin or Owner only
 * - coMaster: Player, plus a live Secondary row on a table - it runs a table without the Master role
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

type ActorName = 'player' | 'master' | 'admin' | 'owner' | 'coMaster'

interface Roles {
  asMaster?: boolean
  asAdmin?: boolean
  asOwner?: boolean
}

const actors: Record<ActorName, { discordId: string; roles: Roles }> = {
  player: { discordId: `e2e-gates-player-${runId}`, roles: {} },
  master: { discordId: `e2e-gates-master-${runId}`, roles: { asMaster: true } },
  admin: { discordId: `e2e-gates-admin-${runId}`, roles: { asAdmin: true } },
  owner: { discordId: `e2e-gates-owner-${runId}`, roles: { asOwner: true } },
  coMaster: { discordId: `e2e-gates-co-${runId}`, roles: {} },
}
const otherMaster = { discordId: `e2e-gates-master2-${runId}`, roles: { asMaster: true } }

/** Signs in through the test login and returns the access token; the response also sets the session cookie. */
async function login(request: APIRequestContext, discordId: string, roles: Roles): Promise<string> {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: {
      discordId,
      asMaster: roles.asMaster ?? false,
      asAdmin: roles.asAdmin ?? false,
      asOwner: roles.asOwner ?? false,
    },
  })
  expect(response.ok()).toBeTruthy()
  return ((await response.json()) as { accessToken: string }).accessToken
}

/** A fresh browser context signed in as the actor, so no remembered context leaks from one case to the next. */
async function pageOf(browser: Browser, discordId: string, roles: Roles) {
  const context = await browser.newContext()
  await login(context.request, discordId, roles)
  return { context, page: await context.newPage() }
}

const tokens = {} as Record<ActorName, string>
const bearer = (token: string) => ({ Authorization: `Bearer ${token}` })
let tableId: string
let userId: string

test.beforeAll(async () => {
  const api = await playwrightRequest.newContext()
  for (const name of Object.keys(actors) as ActorName[]) {
    tokens[name] = await login(api, actors[name].discordId, actors[name].roles)
  }
  // The co-master has to exist before it can be added; it is added as Secondary, without the Master role.
  const created = await api.post(`${BACKEND_URL}/api/v1/game-tables`, {
    headers: bearer(tokens.master),
    data: runnableTableBody(`Mesa E2E gates ${runId}`),
  })
  expect(created.status()).toBe(201)
  tableId = ((await created.json()) as { id: string }).id

  const me = await api.get(`${BACKEND_URL}/api/v1/users/me`, { headers: bearer(tokens.coMaster) })
  userId = ((await me.json()) as { id: string }).id
  const added = await api.post(`${BACKEND_URL}/api/v1/game-tables/${tableId}/masters`, {
    headers: bearer(tokens.master),
    data: { userId, masterType: 'Secondary' },
  })
  expect(added.ok()).toBeTruthy()
  await api.dispose()
})

/** Where each actor ends up on opening a context prefix: the same path if it has the context, otherwise its own home. */
const entryCases: Record<ActorName, Record<string, string>> = {
  player: { '/player': '/player', '/master': '/player', '/admin': '/player', '/master/tables/new': '/player' },
  master: { '/player': '/master', '/master': '/master', '/admin': '/master', '/master/tables/new': '/master/tables/new' },
  admin: { '/player': '/admin', '/master': '/admin', '/admin': '/admin', '/master/tables/new': '/admin' },
  owner: { '/player': '/admin', '/master': '/admin', '/admin': '/admin', '/master/tables/new': '/admin' },
  // Runs a table by its row, so /master is open; creating one needs the role, so /master/tables/new sends it back.
  coMaster: { '/player': '/player', '/master': '/master', '/admin': '/player', '/master/tables/new': '/master' },
}

test.describe('the interface closes a context to whoever does not have it', () => {
  for (const [name, routes] of Object.entries(entryCases) as [ActorName, Record<string, string>][]) {
    test(`${name}: each prefix stays or redirects to its own home`, async ({ browser }) => {
      for (const [route, finalPath] of Object.entries(routes)) {
        const { context, page } = await pageOf(browser, actors[name].discordId, actors[name].roles)
        try {
          await page.goto(route)
          await expect(page, `${name} opening ${route}`).toHaveURL(finalPath)
          await expect(page.getByText('No tenés permiso')).toBeHidden()
        } finally {
          await context.close()
        }
      }
    })
  }

  test('without a session every context goes to /login', async ({ browser }) => {
    for (const route of ['/player', '/master', '/admin', '/master/tables/new']) {
      const context = await browser.newContext()
      const page = await context.newPage()
      await page.goto(route)
      await expect(page, `anonymous opening ${route}`).toHaveURL(/\/login/)
      await context.close()
    }
  })
})

test.describe('the transversal screens belong to no context', () => {
  const transversals = ['/profile', '/notifications', '/my/schedule', '/my/files', '/help']

  for (const name of Object.keys(actors) as ActorName[]) {
    test(`${name}: none of them redirects`, async ({ browser }) => {
      const { context, page } = await pageOf(browser, actors[name].discordId, actors[name].roles)
      try {
        for (const route of [...transversals, `/users/${userId}`]) {
          await page.goto(route)
          await expect(page, `${name} opening ${route}`).toHaveURL(route)
          await expect(page.getByRole('banner')).toBeVisible()
        }
      } finally {
        await context.close()
      }
    })
  }
})

test.describe('the backend does not rely on the interface', () => {
  const adminUsers: [ActorName, number][] = [
    ['player', 403],
    ['master', 403],
    ['coMaster', 403],
    ['admin', 200],
    ['owner', 200],
  ]
  for (const [name, status] of adminUsers) {
    test(`GET /admin/users answers ${status} to ${name}`, async ({ request }) => {
      const response = await request.get(`${BACKEND_URL}/api/v1/admin/users`, { headers: bearer(tokens[name]) })
      expect(response.status()).toBe(status)
    })
  }

  test('GET /admin/users answers 401 without a token', async ({ request }) => {
    expect((await request.get(`${BACKEND_URL}/api/v1/admin/users`)).status()).toBe(401)
  })

  for (const name of ['player', 'admin', 'owner', 'coMaster'] as ActorName[]) {
    test(`POST /game-tables answers 403 to ${name}, who lacks the Master role`, async ({ request }) => {
      const response = await request.post(`${BACKEND_URL}/api/v1/game-tables`, {
        headers: bearer(tokens[name]),
        data: runnableTableBody(`Mesa E2E gates denied ${name} ${runId}`),
      })
      expect(response.status()).toBe(403)
    })
  }

  test('the lists a context shows answer 200 to a session that lacks the context', async ({ request }) => {
    for (const path of ['/game-tables/managed', '/game-tables/mine', '/registrations/mine']) {
      const response = await request.get(`${BACKEND_URL}/api/v1${path}`, { headers: bearer(tokens.admin) })
      expect(response.status(), path).toBe(200)
    }
  })
})

test("a master opening another master's table sees the forbidden state inside their own context", async ({ browser }) => {
  const { context, page } = await pageOf(browser, otherMaster.discordId, otherMaster.roles)
  try {
    await page.goto(`/master/tables/${tableId}`)
    await expect(page).toHaveURL(`/master/tables/${tableId}`)
    await expect(page.getByText('No tenés permiso')).toBeVisible()
    await expect(page.getByRole('navigation', { name: /./ }).first()).toBeVisible()
  } finally {
    await context.close()
  }
})

test.describe('an unknown address under a context shows the not-found screen with its header', () => {
  const cases: [ActorName, string][] = [
    ['master', '/master/does/not-exist'],
    ['coMaster', '/master/does-not-exist'],
    ['admin', '/admin/does/not-exist'],
    ['owner', '/admin/does-not-exist'],
  ]
  for (const [name, route] of cases) {
    test(`${name} on ${route}`, async ({ browser }) => {
      const { context, page } = await pageOf(browser, actors[name].discordId, actors[name].roles)
      try {
        await page.goto(route)
        await expect(page).toHaveURL(route)
        await expect(page.getByRole('heading', { name: '404' })).toBeVisible()
        await expect(page.getByRole('banner')).toBeVisible()
        await expect(page.getByRole('link', { name: 'Volver al inicio' })).toBeVisible()
      } finally {
        await context.close()
      }
    })
  }
})
