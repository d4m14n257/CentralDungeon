import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

import { applyToTable } from './helpers/application'
import { addScheduleSlot, chooseRequiredCatalogs, submitForReview } from './helpers/tableWizard'
import { approveTableFromQueue } from './helpers/adminQueue'

/**
 * F2.3 end to end: the profile, and the asymmetry that #41 is about.
 *
 * **The claim worth making here is the negative one.** The five visibility rules of
 * `modelo-datos.md` §5 had been written for a year with nothing implementing them, so what has to be
 * proven is not that a profile renders — it is that somebody with no relationship to another person
 * cannot reach theirs, and that the refusal reads as «ya no podés ver este perfil» rather than
 * confirming the person exists (#249).
 *
 * Login through TestLoginController (the backend's `test` profile), like every other spec.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

/**
 * Signs in without going through Discord: the `test` profile's shortcut issues the same tokens the
 * real login does.
 *
 * @param request   the browser's network context, so the refresh cookie lands in it
 * @param discordId which identity to sign in as
 * @param asMaster  whether to sign in with the Master role
 * @param asAdmin   whether to sign in with the Admin role
 */
async function testLogin(request: APIRequestContext, discordId: string, asMaster = false, asAdmin = false) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asMaster, asAdmin },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as { accessToken: string }
}

/**
 * Opens a tab that is already signed in, and reports the user id the session belongs to.
 *
 * The id matters here in a way it does not in other specs: half of these assertions are about
 * opening *somebody else's* profile by id.
 *
 * @param browser   the test's browser
 * @param discordId which identity to sign in as
 * @param asMaster  whether to sign in with the Master role
 * @param asAdmin   whether to sign in with the Admin role
 */
async function newAuthenticatedPage(browser: Browser, discordId: string, asMaster: boolean, asAdmin: boolean) {
  const context = await browser.newContext()
  const { accessToken } = await testLogin(context.request, discordId, asMaster, asAdmin)
  const me = await context.request.get(`${BACKEND_URL}/api/v1/users/me`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  expect(me.ok()).toBeTruthy()
  const { id } = (await me.json()) as { id: string }
  const page = await context.newPage()
  return { context, page, id }
}

/**
 * Builds a table through the wizard and returns its id.
 *
 * @param page     the tab, authenticated as a master
 * @param name     the table's name
 * @param hourtime the weekly slot, so two tables in one spec do not collide under R1 (#178)
 */
async function createTable(page: Page, name: string, hourtime: string): Promise<string> {
  await page.goto('/master/tables/new')
  await page.getByRole('textbox', { name: 'Nombre' }).fill(name)
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await chooseRequiredCatalogs(page)
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await addScheduleSlot(page, hourtime)
  await page.getByRole('button', { name: 'Siguiente' }).click()

  // The files step (#228): nothing is required there, so it is walked past.
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await page.getByRole('button', { name: 'Crear mesa' }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()

  const id = page.url().split('/master/tables/')[1]
  expect(id).toBeTruthy()
  return id as string
}

/** Sends the table to review and approves it, which is what opens it to applications. */
async function open(masterPage: Page, adminPage: Page, tableId: string, name: string) {
  await submitForReview(masterPage, tableId)
  await approveTableFromQueue(adminPage, name)
}

/**
 * The two halves of #41, in the order the rules describe them: the player looks at the master
 * *before* applying, and the master looks at the candidate only once the application arrives.
 */
test('the asymmetry of #41: a player reads the master first, the master reads the candidate after', async ({ browser }) => {
  const tableName = `Mesa Perfil E2E ${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-prof-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-prof-admin-${runId}`, false, true)
  const player = await newAuthenticatedPage(browser, `e2e-prof-player-${runId}`, false, false)

  try {
    const tableId = await createTable(master.page, tableName, '20:00')
    await open(master.page, admin.page, tableId, tableName)

    // #41a — the master of a table anybody can see is a profile anybody can read, before applying.
    await player.page.goto(`/player/tables/${tableId}`)
    await player.page
      .getByRole('link', { name: /e2e-prof-master/ })
      .first()
      .click()
    await expect(player.page).toHaveURL(new RegExp(`/player/users/${master.id}$`))
    await expect(player.page.getByRole('heading', { name: 'Asistencia' })).toBeVisible()

    // #248 — karma and comments are F5, so the wireframe's lower half is not drawn at all.
    await expect(player.page.getByText('Comentarios recibidos')).toBeHidden()

    // #41b — and only now, with the application in, does the candidate's profile open for the master.
    await player.page.goto(`/player/tables/${tableId}`)
    await applyToTable(player.page)

    await master.page.goto(`/master/tables/${tableId}`)
    await master.page
      .getByRole('link', { name: /e2e-prof-player/ })
      .first()
      .click()
    await expect(master.page).toHaveURL(new RegExp(`/player/users/${player.id}$`))
  } finally {
    await player.context.close()
    await admin.context.close()
    await master.context.close()
  }
})

/**
 * The negative case, which is the one that matters. Two people the platform never put in the same
 * room cannot read each other, and the screen says so without confirming anybody exists (#44, #249).
 */
test('somebody with no table in common cannot read another profile', async ({ browser }) => {
  const one = await newAuthenticatedPage(browser, `e2e-prof-stranger-a-${runId}`, false, false)
  const other = await newAuthenticatedPage(browser, `e2e-prof-stranger-b-${runId}`, false, false)

  try {
    await one.page.goto(`/player/users/${other.id}`)
    await expect(one.page.getByText('Ya no podés ver este perfil')).toBeVisible()
  } finally {
    await one.context.close()
    await other.context.close()
  }
})

/** #45 — an admin has no visibility restrictions, on the same pair the test above refuses. */
test('an admin reads a profile nobody else could', async ({ browser }) => {
  const admin = await newAuthenticatedPage(browser, `e2e-prof-admin2-${runId}`, false, true)
  const stranger = await newAuthenticatedPage(browser, `e2e-prof-stranger-c-${runId}`, false, false)

  try {
    await admin.page.goto(`/player/users/${stranger.id}`)
    await expect(admin.page.getByText('Ya no podés ver este perfil')).toBeHidden()
    await expect(admin.page.getByRole('heading', { name: 'Asistencia' })).toBeVisible()
  } finally {
    await stranger.context.close()
    await admin.context.close()
  }
})

/** The reader's own profile is always reachable, and from the account menu (#248). */
test('the account menu leads to your own profile', async ({ browser }) => {
  const me = await newAuthenticatedPage(browser, `e2e-prof-self-${runId}`, false, false)

  try {
    await me.page.goto('/player')
    await me.page.getByRole('button', { name: 'Mi cuenta' }).click()
    await me.page.getByRole('menuitem', { name: 'Mi perfil' }).click()

    await expect(me.page).toHaveURL(/\/player\/profile$/)
    await expect(me.page.getByRole('heading', { name: 'Asistencia' })).toBeVisible()
  } finally {
    await me.context.close()
  }
})
