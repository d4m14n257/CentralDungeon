import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

import { applyToTable } from './helpers/application'
import { approveTableFromQueue } from './helpers/adminQueue'
import { addScheduleSlot, chooseRequiredCatalogs, submitForReview } from './helpers/tableWizard'

/**
 * F3.4 end to end, against the real backend: the two "se prueba" sentences of
 * `fase-3-admin-owner.md` §4, which are the only place the slice can be proven at all.
 *
 * > *Un master pide pausa y un admin la aprueba; el calendario del jugador se congela. Un
 * > `Primary` veta a alguien: esa persona deja de ver la mesa en el explorador, recibe `404` en el
 * > detalle y `404` en el archivo que antes descargaba.*
 *
 * **What no unit test proves and this does.** The veto is a rule with seven front doors (§7 of the
 * same document: «el veto toca seis vías de lectura y es fácil cerrar cinco»), and a unit test can
 * only ever ask one door at a time with the other six mocked away. Here the same person walks the
 * explorer, the detail and the download **in one browser session** before and after the veto, so a
 * door left open is a failing assertion rather than a test nobody wrote. The pause is the mirror
 * case: `PauseRequested` was an orphan state for three phases, and the thing worth proving is not
 * that a column changed but that the request crosses into an admin's tray and the answer reaches
 * the player's calendar — three screens and two actors, none of them anybody's unit.
 *
 * It is also the spec F3.4 shipped without. The slice reached review with 165 integration tests and
 * zero Playwright, which is the one gap F3.3 had already written the lesson for: «lo encontró
 * Playwright, no los tests ni la revisión».
 *
 * Login through TestLoginController (the backend's `test` profile), like every other spec.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

/** The community's usual slot, and the hour the materialized sessions take (#230). */
const FRIDAY_EVENING = '20:00'

async function testLogin(request: APIRequestContext, discordId: string, asMaster = false, asAdmin = false) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asMaster, asAdmin },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as { accessToken: string }
}

async function newAuthenticatedPage(browser: Browser, discordId: string, asMaster: boolean, asAdmin: boolean) {
  const context = await browser.newContext()
  const { accessToken } = await testLogin(context.request, discordId, asMaster, asAdmin)
  const page = await context.newPage()
  return { context, page, accessToken }
}

/**
 * A table with a real calendar, sent for review.
 *
 * The start date and the session count are what materialization needs (#26, #33), and the pause half
 * of this spec is about sessions disappearing — so a table without them would have nothing to freeze.
 *
 * @param page    the tab, already authenticated as a master
 * @param name    the table's name
 * @param weekday the agenda's day. Two tables for one master need different ones: R1 of #178 refuses
 *                overlapping agendas and every table carries one (#226)
 * @returns the id of the created table
 */
async function createTableWithCalendar(page: Page, name: string, weekday = 'Viernes'): Promise<string> {
  await page.goto('/master/tables/new')
  await page.getByRole('textbox', { name: 'Nombre' }).fill(name)
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await chooseRequiredCatalogs(page)
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await page.getByLabel('Comienza a partir de').fill('2026-09-11')
  await addScheduleSlot(page, FRIDAY_EVENING, weekday)
  await page.getByRole('button', { name: 'Siguiente' }).click()

  // The files step (#228): nothing is required there, so it is walked past.
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await page.getByLabel('Sesiones planeadas').fill('4')
  await page.getByRole('button', { name: 'Crear mesa' }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()

  const id = page.url().split('/master/tables/')[1]
  expect(id).toBeTruthy()
  // Born in Draft since #245: nothing an admin can approve until it is sent.
  await submitForReview(page, id as string)
  return id as string
}

/**
 * Takes somebody from applicant to player, which both halves of this spec need.
 *
 * @param master     the master's page
 * @param player     the applicant's page
 * @param tableId    the table
 * @param playerName the applicant's discord handle, which is how the row is found
 */
async function acceptAsPlayer(master: Page, player: Page, tableId: string, playerName: string) {
  await player.goto(`/player/tables/${tableId}`)
  await applyToTable(player)

  await master.goto(`/master/tables/${tableId}`)
  const candidate = master.getByRole('listitem').filter({ hasText: playerName }).first()
  await candidate.getByRole('button', { name: 'Aceptar' }).click()
  await master.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
}

/**
 * The first sentence: the pause travels from a master, through an admin, to a player's calendar.
 *
 * **Three things are asserted that no single layer owns.** That asking moves the table into
 * `PauseRequested` and *says so instead of claiming a pause* — a master who reads "pausada" there
 * stops turning up while the calendar is still promising dates. That the request lands in the shared
 * tray as an admin's work, which is what makes `TablePause` a request rather than a column. And that
 * the admin's yes reaches the player: the pending sessions leave the calendar, which is the whole
 * point of freezing an agenda (#32, #33) and is derived on read rather than deleted, so the same
 * screen is the only place it can be seen.
 */
test('a master asks for a pause, an admin grants it, and the player calendar freezes', async ({ browser }) => {
  const tableName = `Mesa Pausa E2E ${runId}`
  const playerDiscordId = `e2e-pause-player-${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-pause-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-pause-admin-${runId}`, false, true)
  const player = await newAuthenticatedPage(browser, playerDiscordId, false, false)

  try {
    const tableId = await createTableWithCalendar(master.page, tableName)
    await approveTableFromQueue(admin.page, tableName)
    await acceptAsPlayer(master.page, player.page, tableId, playerDiscordId)

    // A pause only makes sense on a table that is actually running.
    await master.page.goto(`/master/tables/${tableId}/status`)
    await master.page.getByRole('button', { name: 'Iniciar mesa' }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
    await expect(master.page.getByText('En curso', { exact: true })).toBeVisible()

    // The calendar the player has before anything is asked: this is what has to disappear later, and
    // asserting it now is what stops the freeze assertion from passing on an empty screen.
    await player.page.goto(`/player/my-tables/${tableId}`)
    await expect(player.page.getByText('Sesión 1')).toBeVisible()

    // Asking. The reason is mandatory (#32) and it is what the admin reads in the tray.
    await master.page.getByRole('button', { name: 'Pedir pausa' }).click()
    const pauseDialog = master.page.getByRole('dialog')
    await pauseDialog.getByRole('textbox').fill('Me operan y no puedo dirigir por un mes')
    await pauseDialog.getByRole('button', { name: 'Pedir pausa' }).click()
    await expect(pauseDialog).toBeHidden()

    // Asking is not pausing, and the screen says which of the two happened.
    await expect(master.page.getByText('Pausa solicitada', { exact: true })).toBeVisible()
    await expect(master.page.getByText(/todavía no te respondieron/)).toBeVisible()
    await expect(master.page.getByRole('button', { name: 'Pedir pausa' })).toBeHidden()

    // The player's dates still stand: nothing has been granted, so nothing is frozen yet.
    await player.page.goto(`/player/my-tables/${tableId}`)
    await expect(player.page.getByText('Sesión 1')).toBeVisible()

    // It reached the admins as work waiting on them, which is the half `PauseRequested` never had.
    await admin.page.goto('/admin/queue')
    const pauseRow = admin.page.getByRole('row').filter({ hasText: 'Pausa de mesa' })
    await expect(pauseRow).toBeVisible()
    await expect(pauseRow).toContainText('Me operan y no puedo dirigir por un mes')

    await pauseRow.getByRole('button', { name: 'Aprobar' }).click()
    const resolveDialog = admin.page.getByRole('dialog')
    await resolveDialog.getByRole('textbox').fill('Aprobada: avisá cuando puedas retomar')
    await resolveDialog.getByRole('button', { name: 'Aprobar' }).click()
    await expect(resolveDialog).toBeHidden()
    // Resolved work is not work waiting on anybody.
    await expect(pauseRow).toBeHidden()

    await master.page.goto(`/master/tables/${tableId}/status`)
    await expect(master.page.getByText('Pausada', { exact: true })).toBeVisible()
    // The admin's reason is the pause's justification: they wrote why once and it is recorded once
    // (#32, modelo-datos.md:835).
    await expect(master.page.getByText('Aprobada: avisá cuando puedas retomar')).toBeVisible()

    // And this is the sentence the slice promised: the calendar froze. A paused table promises no
    // dates, so the pending sessions stop being shown - to its master either.
    await player.page.goto(`/player/my-tables/${tableId}`)
    await expect(player.page.getByRole('heading', { name: tableName })).toBeVisible()
    await expect(player.page.getByText('Sesión 1')).toBeHidden()
  } finally {
    await player.context.close()
    await admin.context.close()
    await master.context.close()
  }
})

/**
 * The second sentence, and the one §7 named the failure mode for in advance.
 *
 * The same player walks all three doors **before** the veto - the explorer, the detail, the download
 * - and then walks them again afterwards. That order is the test: a spec that only checked the
 * after-state would pass just as well against a table the player could never reach in the first
 * place, which is exactly how five of six doors get closed and nobody notices the sixth.
 *
 * And the answer is `404` every time, never `403` (#29): a 403 confirms what the 404 denies.
 */
test('a Primary vetoes a player, who stops seeing the table, its detail and its file', async ({ browser }) => {
  const tableName = `Mesa Veto E2E ${runId}`
  const playerDiscordId = `e2e-veto-player-${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-veto-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-veto-admin-${runId}`, false, true)
  const player = await newAuthenticatedPage(browser, playerDiscordId, false, false)

  try {
    const tableId = await createTableWithCalendar(master.page, tableName, 'Sábado')
    await approveTableFromQueue(admin.page, tableName)

    // A shared attachment, which is the door #206 anticipated the bug on: until F3.4 the condition
    // was «not private» alone, so any authenticated person reached it - the vetoed included.
    await master.page.goto(`/master/tables/${tableId}/files`)
    await master.page.getByRole('button', { name: 'Agregar un archivo' }).click()
    const fileDialog = master.page.getByRole('dialog')
    await fileDialog
      .locator('input[type="file"]')
      .setInputFiles({ name: 'mapa-e2e.pdf', mimeType: 'application/pdf', buffer: Buffer.from(`%PDF-1.4 ${runId}`) })
    await fileDialog.getByRole('button', { name: /Agregar \d+ archivos?/ }).click()
    await expect(fileDialog).toBeHidden()
    await expect(master.page.getByText('mapa-e2e.pdf')).toBeVisible()

    await acceptAsPlayer(master.page, player.page, tableId, playerDiscordId)

    // ---------------------------------------------------------- the three doors, while they are open

    await player.page.goto('/player')
    await expect(player.page.getByRole('link', { name: new RegExp(tableName) })).toBeVisible()

    await player.page.goto(`/player/tables/${tableId}`)
    await expect(player.page.getByRole('heading', { name: tableName })).toBeVisible()

    // Asked over HTTP rather than through the download button: what is being pinned is the status
    // code the slice promised, and a started-then-cancelled browser download cannot show one. The
    // token goes in the header because that is how the application authenticates - the JWT is not a
    // cookie, so `page.request` on its own would be an anonymous caller (#122).
    const fileId = await fileIdOfTable(player.page, player.accessToken, tableId)
    expect(await fileStatusFor(player.page, player.accessToken, fileId)).toBe(200)

    // ------------------------------------------------------------------------------------ the veto

    await master.page.goto(`/master/tables/${tableId}/players`)
    const rosterRow = master.page.getByRole('listitem').filter({ hasText: playerDiscordId })
    await rosterRow.getByRole('button', { name: 'Vetar' }).click()
    const vetoDialog = master.page.getByRole('dialog')
    // The dialog says what it costs the other person before the press, not after.
    await expect(vetoDialog.getByText(/no le aparece en el explorador/)).toBeVisible()
    await vetoDialog.getByRole('textbox').fill('Faltó a tres sesiones sin avisar')
    await vetoDialog.getByRole('button', { name: 'Vetar' }).click()
    await expect(vetoDialog).toBeHidden()

    // ------------------------------------------------------- the same three doors, now all closed

    await player.page.goto('/player')
    await expect(player.page.getByRole('link', { name: new RegExp(tableName) })).toBeHidden()

    await player.page.goto(`/player/tables/${tableId}`)
    // The `ErrorState` of a 404 detail, which is the same sentence a deleted table gets: from this
    // person's side the table stopped existing, and telling the two apart would be the 403 (#29).
    await expect(player.page.getByText('Puede que ya no esté disponible.')).toBeVisible()
    await expect(player.page.getByRole('heading', { name: tableName })).toBeHidden()

    expect(await fileStatusFor(player.page, player.accessToken, fileId)).toBe(404)

    // ------------------------------------------------- and the master still sees what they decided

    // A veto that vanishes from the interface is not reversible however reversible the backend makes
    // it, so the row stays - with who decided it, when, and the reason somebody will read months
    // later to decide whether it still stands.
    await master.page.goto(`/master/tables/${tableId}/players`)
    const blockedRow = master.page.getByRole('listitem').filter({ hasText: playerDiscordId })
    await expect(blockedRow.getByText('Vetado', { exact: true })).toBeVisible()
    await expect(blockedRow.getByText(/Vetado por/)).toBeVisible()
    await expect(blockedRow.getByText(/Faltó a tres sesiones sin avisar/)).toBeVisible()

    // ------------------------------------------------------------------ and it is really reversible

    await blockedRow.getByRole('button', { name: 'Levantar el veto' }).click()
    const liftDialog = master.page.getByRole('dialog')
    await liftDialog.getByRole('textbox').fill('Hablamos y quedó claro')
    await liftDialog.getByRole('button', { name: 'Levantar el veto' }).click()
    await expect(liftDialog).toBeHidden()

    // Back where they were - `Player`, read from the trail and not assumed.
    await player.page.goto(`/player/tables/${tableId}`)
    await expect(player.page.getByRole('heading', { name: tableName })).toBeVisible()
    expect(await fileStatusFor(player.page, player.accessToken, fileId)).toBe(200)
  } finally {
    await player.context.close()
    await admin.context.close()
    await master.context.close()
  }
})

/**
 * The id of the one file a table shares, read from the detail the player already receives.
 *
 * Taken from the API rather than scraped off the screen: the download button carries no id the DOM
 * exposes, and the point of the assertion is the status code of a request, not the markup.
 *
 * @param page        the reader's page, for its request context
 * @param accessToken  the reader's token - the API authenticates by header, never by cookie (#122)
 * @param tableId      the table
 * @returns the id of its first shared file
 */
async function fileIdOfTable(page: Page, accessToken: string, tableId: string): Promise<string> {
  const response = await page.request.get(`${BACKEND_URL}/api/v1/game-tables/${tableId}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  expect(response.ok()).toBeTruthy()
  // `fileId` and not `id`: SharedFileResponse names the field after what the download endpoint takes.
  const detail = (await response.json()) as { files: { fileId: string }[] }
  const first = detail.files[0]
  expect(first?.fileId).toBeTruthy()
  return (first as { fileId: string }).fileId
}

/**
 * The status code this reader gets for a file's bytes.
 *
 * The whole veto assertion is a status code, three times over, so it is asked in one place: 200 while
 * the table is theirs to see, 404 once it is not, 200 again once the veto is lifted.
 *
 * @param page        the reader's page, for its request context
 * @param accessToken the reader's token
 * @param fileId      the file
 * @returns the HTTP status of the download
 */
async function fileStatusFor(page: Page, accessToken: string, fileId: string): Promise<number> {
  const response = await page.request.get(`${BACKEND_URL}/api/v1/files/${fileId}/content`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  return response.status()
}
