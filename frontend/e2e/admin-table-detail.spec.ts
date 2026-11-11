import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

import { acceptReview } from './helpers/review'
import { runnableTableBody } from './helpers/tableApi'

/**
 * The admin's side of one table, end to end against the real backend: the admin home, a table that
 * is not theirs opened from the listing in read-only mode across its eight tabs, and the edit of that
 * table.
 *
 * What only a real run can say: that the master's own tab modules, mounted by the admin, really
 * leave out what is the master's to press (the buttons are absent, not disabled), and that the
 * backend answers the management reads to an admin on every tab.
 *
 * Login through TestLoginController (the backend's `test` profile), like every other spec. Sessions
 * are read in UTC so the agenda is the same text on any machine.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

/** A Friday, so the first session lands on the agenda's weekday. */
const START_DATE = '2027-03-05'

interface Actor {
  context: Awaited<ReturnType<Browser['newContext']>>
  page: Page
}

async function login(request: APIRequestContext, discordId: string, roles: { asMaster?: boolean; asAdmin?: boolean } = {}) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asMaster: roles.asMaster ?? false, asAdmin: roles.asAdmin ?? false },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as { accessToken: string }
}

async function newActor(browser: Browser, discordId: string, roles: { asMaster?: boolean; asAdmin?: boolean } = {}): Promise<Actor> {
  const context = await browser.newContext({ timezoneId: 'UTC' })
  await login(context.request, discordId, roles)
  return { context, page: await context.newPage() }
}

/** POSTs as `token` and fails the test with the body when the backend refuses. */
async function post(request: APIRequestContext, token: string, path: string, data?: unknown) {
  const response = await request.post(`${BACKEND_URL}/api/v1${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    ...(data === undefined ? {} : { data }),
  })
  expect(response.ok(), `${path}: ${response.status()} ${await response.text()}`).toBeTruthy()
  return response
}

/**
 * Builds an open table with a calendar, one candidate, one accepted player and one published request,
 * all through the API: the screens under test are the admin's, not the setup's.
 *
 * @returns the table's id
 */
async function createRichOpenTable(
  request: APIRequestContext,
  names: { table: string; master: string; admin: string; candidate: string; player: string; task: string },
): Promise<string> {
  const master = await login(request, names.master, { asMaster: true })
  const created = await post(
    request,
    master.accessToken,
    '/game-tables',
    runnableTableBody(names.table, { maxPlayers: 4, totalSessions: 4, startDate: START_DATE }),
  )
  const tableId = ((await created.json()) as { id: string }).id
  await post(request, master.accessToken, `/game-tables/${tableId}/submit`)

  const admin = await login(request, names.admin, { asAdmin: true })
  await post(request, admin.accessToken, `/game-tables/${tableId}/approve`)

  // The player applies first and is accepted straight away; the candidate then stays in the queue.
  const player = await login(request, names.player)
  await post(request, player.accessToken, `/game-tables/${tableId}/registrations`, { description: 'Quiero jugar', fileIds: [] })
  const queue = await request.get(`${BACKEND_URL}/api/v1/game-tables/${tableId}/registrations`, {
    headers: { Authorization: `Bearer ${master.accessToken}` },
  })
  const registrationId = ((await queue.json()) as { content: { id: string }[] }).content[0]?.id
  expect(registrationId).toBeTruthy()
  await post(request, master.accessToken, `/registrations/${registrationId}/accept`)

  const candidate = await login(request, names.candidate)
  await post(request, candidate.accessToken, `/game-tables/${tableId}/registrations`, { description: 'Yo también', fileIds: [] })

  await post(request, master.accessToken, `/game-tables/${tableId}/tasks`, {
    title: names.task,
    audience: 'Players',
    acceptsText: true,
    acceptsFiles: false,
    isMandatory: false,
    fileIds: [],
  })
  return tableId
}

/** The nav of a table's tabs, shared by the master's and the admin's screens. */
function tabs(page: Page) {
  return page.getByRole('navigation', { name: 'Secciones de la mesa' })
}

/**
 * Opens a table from the admin listing, the way an admin gets to one: search its name, follow the link.
 */
async function openFromListing(page: Page, tableName: string) {
  await page.goto('/admin/tables')
  const box = page.getByRole('combobox', { name: 'Buscar mesas' })
  await box.click()
  await box.fill(tableName)
  await box.press('Enter')
  await box.press('Enter')
  await page.getByRole('link', { name: tableName }).click()
}

test('the admin home loads and its tray access leads to the tray, with the count of what waits', async ({ browser, request }) => {
  // A table in review is what makes the tray non-empty, whatever else is in the database.
  const master = await login(request, `e2e-adh-master-${runId}`, { asMaster: true })
  const created = await post(request, master.accessToken, '/game-tables', runnableTableBody(`Mesa Home Admin E2E ${runId}`))
  await post(request, master.accessToken, `/game-tables/${((await created.json()) as { id: string }).id}/submit`)

  const admin = await newActor(browser, `e2e-adh-admin-${runId}`, { asAdmin: true })
  try {
    await admin.page.goto('/admin')
    await expect(admin.page.getByRole('heading', { name: 'Te damos la bienvenida a la administración' })).toBeVisible()

    // The card, not the nav's own «Bandeja» link: it is the one that carries the count.
    const trayLink = admin.page.getByRole('main').getByRole('link', { name: /ítems? esperan? una acción/ })
    await expect(trayLink).toContainText('Bandeja')

    await trayLink.click()
    await expect(admin.page).toHaveURL(/\/admin\/queue$/)
    await expect(admin.page.getByRole('row').filter({ hasText: `Mesa Home Admin E2E ${runId}` })).toBeVisible()
  } finally {
    await admin.context.close()
  }
})

test("an admin opens another master's table from the listing and reads its eight tabs without the master's buttons", async ({
  browser,
  request,
}) => {
  const names = {
    table: `Mesa Detalle Admin E2E ${runId}`,
    master: `e2e-adt-master-${runId}`,
    admin: `e2e-adt-setup-admin-${runId}`,
    candidate: `e2e-adt-candidate-${runId}`,
    player: `e2e-adt-player-${runId}`,
    task: `Ficha Detalle E2E ${runId}`,
  }
  const tableId = await createRichOpenTable(request, names)
  const admin = await newActor(browser, `e2e-adt-admin-${runId}`, { asAdmin: true })
  const page = admin.page

  /** Follows one tab of the nav and says it landed there. */
  async function goToTab(name: string, path: string) {
    await tabs(page).getByRole('link', { name, exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`/admin/tables/${tableId}/${path}$`))
  }

  try {
    await openFromListing(page, names.table)

    // ---- Details first, and the header carries the admin's own edit, not the master's.
    await expect(page).toHaveURL(new RegExp(`/admin/tables/${tableId}$`))
    await expect(page.getByRole('heading', { name: names.table })).toBeVisible()
    const labels = await tabs(page).getByRole('link').allInnerTexts()
    expect(labels).toEqual(['Detalles', 'Candidatos', 'Jugadores', 'Agenda', 'Sesiones', 'Peticiones', 'Archivos', 'Historial'])
    await expect(tabs(page).getByRole('link', { name: 'Detalles' })).toHaveAttribute('aria-current', 'page')
    await expect(page.getByText('Comienza a partir de')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Editar mesa' })).toBeVisible()

    // ---- Candidates: the queue is readable, and nothing in it can be decided.
    await goToTab('Candidatos', 'candidates')
    await expect(page.getByText(new RegExp(`^1\\. ${names.candidate}`))).toBeVisible()
    await expect(page.getByRole('button', { name: 'Aceptar', exact: true })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Rechazar', exact: true })).toHaveCount(0)

    // ---- Players: who directs and who plays, with no veto and no way to add a co-master.
    await goToTab('Jugadores', 'players')
    await expect(page.getByText('Quién dirige esta mesa')).toBeVisible()
    await expect(page.getByText(names.player)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Vetar', exact: true })).toHaveCount(0)
    await expect(page.getByText('Agregar un co-master')).toHaveCount(0)

    // ---- Agenda: the weekly slot, read in UTC, and no way to edit it from here.
    await goToTab('Agenda', 'schedule')
    await expect(page.getByText('Viernes 20:00–23:00')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Editar la mesa', exact: true })).toHaveCount(0)

    // ---- Sessions: approval materialized the calendar; none of it can be played or cancelled.
    await goToTab('Sesiones', 'sessions')
    await expect(page.getByText('Calendario · 4 sesiones')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Marcar como jugada' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Cancelar sesión' })).toHaveCount(0)

    // ---- Requests: the published one is listed, and nothing publishes, edits or closes.
    await goToTab('Peticiones', 'tasks')
    await expect(page.getByText(names.task)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Publicar petición' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Editar la petición' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Cerrar la petición' })).toHaveCount(0)

    // ---- Files: the section loaded (empty here), and attaching is the master's.
    await goToTab('Archivos', 'files')
    await expect(page.getByRole('heading', { name: 'Archivos de la mesa' })).toBeVisible()
    await expect(page.getByText('La mesa no tiene archivos')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Agregar un archivo' })).toHaveCount(0)

    // ---- History last: the steps the table went through, who took each.
    await goToTab('Historial', 'history')
    await expect(page.getByText(/→/).first()).toBeVisible()
    await expect(page.getByText(/^Por /).first()).toBeVisible()
  } finally {
    await admin.context.close()
  }
})

test("an admin rewrites another master's table and the change shows on its detail", async ({ browser, request }) => {
  const names = {
    table: `Mesa Edicion Admin E2E ${runId}`,
    master: `e2e-ade-master-${runId}`,
    admin: `e2e-ade-setup-admin-${runId}`,
    candidate: `e2e-ade-candidate-${runId}`,
    player: `e2e-ade-player-${runId}`,
    task: `Ficha Edicion E2E ${runId}`,
  }
  const renamed = `${names.table} corregida`
  const tableId = await createRichOpenTable(request, names)
  const admin = await newActor(browser, `e2e-ade-admin-${runId}`, { asAdmin: true })
  const page = admin.page

  try {
    await openFromListing(page, names.table)
    await page.getByRole('link', { name: 'Editar mesa' }).click()
    await expect(page).toHaveURL(new RegExp(`/admin/tables/${tableId}/edit$`))

    // The form opens with the table's own data.
    const name = page.getByRole('textbox', { name: 'Nombre' })
    await expect(name).toHaveValue(names.table)
    await expect(page.getByText(/Estás editando una mesa que no es tuya/)).toBeVisible()

    await name.fill(renamed)
    await page.getByRole('button', { name: 'Guardar cambios' }).click()
    await acceptReview(page)

    await expect(page).toHaveURL(new RegExp(`/admin/tables/${tableId}$`))
    await expect(page.getByRole('heading', { name: renamed })).toBeVisible()
  } finally {
    await admin.context.close()
  }
})
