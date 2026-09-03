import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

/**
 * F1.3 end to end, against the real backend: the calendar that gets materialized when the table
 * opens, the four things a master does with it, and what the player sees on `/my/tables/:id`.
 *
 * What no unit test proves and this does: that approving a table **really creates the sessions** out
 * of its agenda, that cancelling one adds a replacement at the end (#194), and that the attendance a
 * master records is the one the player reads, as the three numbers of #137 and not as a percentage.
 *
 * Login through TestLoginController (the backend's `test` profile), like every other spec.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

/** A Friday at 20:00 local time, the community's usual slot. */
const FRIDAY_EVENING = '20:00'

/** Four sessions: enough to see the numbering and the replacement without filling the screen. */
const TOTAL_SESSIONS = '4'

async function testLogin(request: APIRequestContext, discordId: string, asMaster = false, asAdmin = false) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asMaster, asAdmin },
  })
  expect(response.ok()).toBeTruthy()
}

async function newAuthenticatedPage(browser: Browser, discordId: string, asMaster: boolean, asAdmin: boolean) {
  const context = await browser.newContext()
  await testLogin(context.request, discordId, asMaster, asAdmin)
  const page = await context.newPage()
  return { context, page }
}

/**
 * Builds a complete table: name, start date, duration, one weekly slot and the session count. The
 * last three are what materialization needs (#26, #33).
 *
 * @param page the tab, already authenticated as a master
 * @param name the table's name
 * @returns the id of the created table
 */
async function createTableWithCalendar(page: Page, name: string): Promise<string> {
  await page.goto('/master/tables/new')
  await page.getByRole('textbox', { name: 'Nombre' }).fill(name)
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await page.getByLabel('Primera sesión').fill('2026-09-11T20:00')
  await page.getByLabel('Duración de una sesión').fill('03:00')
  await page.getByLabel('Hora', { exact: true }).fill(FRIDAY_EVENING)
  await page.getByRole('button', { name: 'Agregar' }).click()
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await page.getByLabel('Sesiones planeadas').fill(TOTAL_SESSIONS)
  await page.getByRole('button', { name: 'Crear mesa' }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()

  const id = page.url().split('/master/tables/')[1]
  expect(id).toBeTruthy()
  return id as string
}

/** Approves the table from `/admin/tables`, which is what triggers materialization. */
async function approve(page: Page, name: string) {
  await page.goto('/admin/tables')
  const row = page.getByRole('listitem').filter({ hasText: name })
  await row.getByRole('button', { name: 'Aprobar' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
  await expect(row).toBeHidden()
}

/**
 * The central scenario: opening the table creates the four sessions, and the master corrects, closes
 * and cancels them from the tab.
 */
test('opening a table materializes its calendar, and the master can run it', async ({ browser }) => {
  const tableName = `Mesa Sesiones E2E ${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-sess-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-sess-admin-${runId}`, false, true)

  try {
    const tableId = await createTableWithCalendar(master.page, tableName)
    await approve(admin.page, tableName)

    await master.page.goto(`/master/tables/${tableId}/sessions`)

    // The four sessions exist, numbered, without anybody having typed them in.
    await expect(master.page.getByRole('button', { name: /^Sesión 1/ })).toBeVisible()
    await expect(master.page.getByRole('button', { name: /^Sesión 4/ })).toBeVisible()

    // Marking a session played is its own action, separate from attendance (#195).
    await master.page.getByRole('button', { name: 'Marcar como jugada' }).first().click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
    // `exact`: the confirmation toast reads "Sesión marcada como jugada" and contains the word too.
    await expect(master.page.getByText('Jugada', { exact: true })).toBeVisible()
  } finally {
    await admin.context.close()
    await master.context.close()
  }
})

/**
 * #194 with the warning up front: cancelling a session adds a replacement at the end, and the UI says
 * so **before** the master commits, not after an unexpected session shows up.
 */
test('cancelling a session warns about the replacement first, then adds it at the end', async ({ browser }) => {
  const tableName = `Mesa Reposicion E2E ${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-repo-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-repo-admin-${runId}`, false, true)

  try {
    const tableId = await createTableWithCalendar(master.page, tableName)
    await approve(admin.page, tableName)
    await master.page.goto(`/master/tables/${tableId}/sessions`)

    await expect(master.page.getByRole('button', { name: /^Sesión 5/ })).toBeHidden()

    await master.page.getByRole('button', { name: 'Cancelar sesión' }).nth(1).click()
    // The reason is in the dialog, before confirming: the table still plays the same number.
    await expect(master.page.getByRole('dialog').getByText(/suma otra al final/)).toBeVisible()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()

    await expect(master.page.getByText('Cancelada', { exact: true })).toBeVisible()
    await expect(master.page.getByRole('button', { name: /^Sesión 5/ })).toBeVisible()
  } finally {
    await admin.context.close()
    await master.context.close()
  }
})

/**
 * The other side: the master records the attendance and the player reads it on `/my/tables/:id`, as
 * the three numbers of #137 — never a percentage, which would hide the distinction that matters.
 */
test('a player sees their own calendar and their attendance as three numbers', async ({ browser }) => {
  const tableName = `Mesa Asistencia E2E ${runId}`
  const playerDiscordId = `e2e-att-player-${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-att-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-att-admin-${runId}`, false, true)
  const player = await newAuthenticatedPage(browser, playerDiscordId, false, false)

  try {
    const tableId = await createTableWithCalendar(master.page, tableName)
    await approve(admin.page, tableName)

    await player.page.goto(`/tables/${tableId}`)
    // The public detail already shows the real calendar, not only the weekly shape.
    await expect(player.page.getByText('Sesión 1')).toBeVisible()
    await player.page.getByRole('button', { name: 'Postularme' }).click()
    await player.page.getByRole('dialog').getByRole('button', { name: 'Postularme' }).click()

    await master.page.goto(`/master/tables/${tableId}`)
    const candidate = master.page.getByRole('listitem').filter({ hasText: playerDiscordId }).first()
    await candidate.getByRole('button', { name: 'Aceptar' }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()

    await master.page.goto(`/master/tables/${tableId}/sessions`)
    await master.page.getByRole('button', { name: /^Sesión 1/ }).click()
    // The shadcn Select is not a native <select>: it is opened and the option is picked from the list.
    await master.page.getByRole('combobox', { name: /^Asistencia de/ }).click()
    await master.page.getByRole('option', { name: 'Presente' }).click()
    await master.page.getByRole('button', { name: 'Guardar asistencia' }).click()

    await player.page.goto(`/my/tables/${tableId}`)
    await expect(player.page.getByRole('heading', { name: tableName })).toBeVisible()
    await expect(player.page.getByText('Mis sesiones')).toBeVisible()
    // The summary, not the session row: both say "Presente" and they are different things.
    await expect(player.page.getByRole('term').filter({ hasText: 'Presente' })).toBeVisible()
    // Three numbers and their denominator; never a "1 of 4" and never a percentage (#137).
    await expect(player.page.getByText('Sesiones registradas')).toBeVisible()
    await expect(player.page.getByText('%')).toBeHidden()
  } finally {
    await player.context.close()
    await admin.context.close()
    await master.context.close()
  }
})
