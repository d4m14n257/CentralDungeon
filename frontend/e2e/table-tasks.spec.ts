import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

/**
 * F1.5 end to end, against the real backend: the criterion of `fase-1-master.md` §4 — *a master
 * publishes a request for their players, the notification reaches them, and they see it on their
 * table*.
 *
 * What no unit test proves and this does: that publishing **actually notifies**, end to end, from the
 * master's dialog to the other person's bell (#77); that an answer with a reused file arrives whole
 * and the master can **open a file that is not theirs** (#63, #206); that a second answer does not
 * replace the first (#76); and that a request aimed at candidates is readable by somebody who has not
 * applied yet, and not answerable by them (#206).
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
}

async function newAuthenticatedPage(browser: Browser, discordId: string, asMaster: boolean, asAdmin: boolean) {
  const context = await browser.newContext()
  await testLogin(context.request, discordId, asMaster, asAdmin)
  const page = await context.newPage()
  return { context, page }
}

/** The smallest table the wizard will make: F1.5 needs somebody to ask things of, not a calendar. */
async function createTable(page: Page, name: string): Promise<string> {
  await page.goto('/master/tables/new')
  await page.getByRole('textbox', { name: 'Nombre' }).fill(name)
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await page.getByRole('button', { name: 'Crear mesa' }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()

  const id = page.url().split('/master/tables/')[1]
  expect(id).toBeTruthy()
  return id as string
}

/** Approves the table from `/admin/tables`, which is what opens it to applications. */
async function approve(page: Page, name: string) {
  await page.goto('/admin/tables')
  const row = page.getByRole('listitem').filter({ hasText: name })
  await row.getByRole('button', { name: 'Aprobar' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
  await expect(row).toBeHidden()
}

/** Applies and gets accepted, which is what turns somebody into a recipient of a `Players` request. */
async function joinAsPlayer(playerPage: Page, masterPage: Page, tableId: string, playerDiscordId: string) {
  await playerPage.goto(`/tables/${tableId}`)
  await playerPage.getByRole('button', { name: 'Postularme' }).click()
  await playerPage.getByRole('dialog').getByRole('button', { name: 'Postularme' }).click()

  await masterPage.goto(`/master/tables/${tableId}`)
  const candidate = masterPage.getByRole('listitem').filter({ hasText: playerDiscordId }).first()
  await candidate.getByRole('button', { name: 'Aceptar' }).click()
  await masterPage.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
}

/**
 * Publishes a request from the Peticiones tab.
 *
 * @param page     the tab, already authenticated as the table's master
 * @param tableId  the table
 * @param title    what is being asked for
 * @param audience the option to pick in the audience selector
 */
async function publishTask(page: Page, tableId: string, title: string, audience: string) {
  await page.goto(`/master/tables/${tableId}/tasks`)
  await page.getByRole('button', { name: 'Publicar petición' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Qué pedís').fill(title)
  await dialog.getByLabel('A quién se lo pedís').click()
  await page.getByRole('option', { name: audience }).click()
  await dialog.getByRole('button', { name: 'Publicar y avisar' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('button', { name: new RegExp(title) })).toBeVisible()
}

/**
 * Types into the rich text editor of the answer dialog.
 *
 * Typed rather than filled: the editor is a ProseMirror `contenteditable`, not an input, and `fill()`
 * hangs on it. Pressing the keys is also what a person actually does, so the editor's own key
 * handling is exercised instead of bypassed.
 *
 * @param page the tab with the answer dialog open
 * @param text what to write
 */
async function writeAnswer(page: Page, text: string) {
  const editor = page.getByRole('dialog').getByRole('textbox', { name: 'Tu respuesta' })
  await editor.click()
  await editor.pressSequentially(text)
}

/** A PDF small enough to be under the cap and real enough for the MIME whitelist to accept it. */
function pdf(name: string, content: string) {
  return { name, mimeType: 'application/pdf', buffer: Buffer.from(`%PDF-1.4 ${content}`) }
}

/**
 * The headline case of the slice: published, notified, seen, answered — and the master reads what
 * arrived, files included.
 */
test('a request reaches the players, and what they hand in reaches the master', async ({ browser }) => {
  const tableName = `Mesa Peticiones E2E ${runId}`
  const playerDiscordId = `e2e-task-player-${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-task-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-task-admin-${runId}`, false, true)
  const player = await newAuthenticatedPage(browser, playerDiscordId, false, false)

  try {
    const tableId = await createTable(master.page, tableName)
    await approve(admin.page, tableName)
    await joinAsPlayer(player.page, master.page, tableId, playerDiscordId)

    await publishTask(master.page, tableId, `Ficha E2E ${runId}`, 'Para los jugadores')

    // #77 end to end: the bell of the other person, not a call somebody mocked.
    await player.page.goto('/notifications')
    await expect(player.page.getByText(new RegExp(`Ficha E2E ${runId}`))).toBeVisible()

    // The player reads it on their own table and answers with text and a file.
    await player.page.goto(`/my/tables/${tableId}`)
    await player.page.getByRole('button', { name: new RegExp(`Ficha E2E ${runId}`) }).click()
    await player.page.getByRole('button', { name: 'Entregar', exact: true }).click()

    const submitDialog = player.page.getByRole('dialog')
    await writeAnswer(player.page, 'Elfa exploradora')
    await submitDialog.getByLabel('Elegir un archivo para subir').setInputFiles(pdf('ficha-tarea-e2e.pdf', runId))
    await expect(submitDialog.getByText('ficha-tarea-e2e.pdf')).toBeVisible()
    await submitDialog.getByRole('button', { name: 'Entregar' }).click()
    await expect(submitDialog).toBeHidden()

    // The master sees the answer, and can open a file that belongs to somebody else - the fifth way
    // a file becomes readable (#63, #206). Without it the row would be visible and the download 404.
    await master.page.goto(`/master/tables/${tableId}/tasks`)
    await master.page.getByRole('button', { name: new RegExp(`Ficha E2E ${runId}`) }).click()
    await expect(master.page.getByText('Elfa exploradora')).toBeVisible()
    const download = master.page.waitForEvent('download')
    await master.page.getByRole('button', { name: 'Descargar' }).first().click()
    expect((await download).suggestedFilename()).toBe('ficha-tarea-e2e.pdf')

    // Nobody is left on the missing roster, and nothing on screen offers to punish anybody (#70).
    await expect(master.page.getByText('Ya entregaron todos.')).toBeVisible()
  } finally {
    await player.context.close()
    await admin.context.close()
    await master.context.close()
  }
})

/**
 * #76 against the real thing: a second answer is a second row. The first one is still there, and the
 * master reads both — the system never decides which of the two counts.
 */
test('a second answer is added and never replaces the first', async ({ browser }) => {
  const tableName = `Mesa Acumula E2E ${runId}`
  const playerDiscordId = `e2e-acc-player-${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-acc-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-acc-admin-${runId}`, false, true)
  const player = await newAuthenticatedPage(browser, playerDiscordId, false, false)

  try {
    const tableId = await createTable(master.page, tableName)
    await approve(admin.page, tableName)
    await joinAsPlayer(player.page, master.page, tableId, playerDiscordId)
    await publishTask(master.page, tableId, `Trasfondo E2E ${runId}`, 'Para los jugadores')

    await player.page.goto(`/my/tables/${tableId}`)
    await player.page.getByRole('button', { name: new RegExp(`Trasfondo E2E ${runId}`) }).click()

    await player.page.getByRole('button', { name: 'Entregar', exact: true }).click()
    await writeAnswer(player.page, 'Primera version')
    await player.page.getByRole('dialog').getByRole('button', { name: 'Entregar' }).click()
    await expect(player.page.getByRole('dialog')).toBeHidden()

    // The button itself says what happens next: answering again, not correcting.
    await player.page.getByRole('button', { name: 'Entregar de nuevo' }).click()
    await writeAnswer(player.page, 'Segunda version')
    await player.page.getByRole('dialog').getByRole('button', { name: 'Entregar' }).click()
    await expect(player.page.getByRole('dialog')).toBeHidden()

    await expect(player.page.getByText('Primera version')).toBeVisible()
    await expect(player.page.getByText('Segunda version')).toBeVisible()

    await master.page.goto(`/master/tables/${tableId}/tasks`)
    await master.page.getByRole('button', { name: new RegExp(`Trasfondo E2E ${runId}`) }).click()
    await expect(master.page.getByText('Primera version')).toBeVisible()
    await expect(master.page.getByText('Segunda version')).toBeVisible()
    // Two answers, one person: the summary counts people, because that is what "handed in" means.
    await expect(master.page.getByText('Entregaron 1 de 1')).toBeVisible()
  } finally {
    await player.context.close()
    await admin.context.close()
    await master.context.close()
  }
})

/**
 * #206 applied to what a table asks: somebody who has not applied reads the requests aimed at
 * candidates — half of deciding whether to apply — and the screen says why they cannot answer yet
 * rather than simply not offering a button.
 */
test('somebody who has not applied reads the candidate requests and is told why they cannot answer', async ({ browser }) => {
  const tableName = `Mesa Candidatos E2E ${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-cand-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-cand-admin-${runId}`, false, true)
  const visitor = await newAuthenticatedPage(browser, `e2e-cand-visitor-${runId}`, false, false)

  try {
    const tableId = await createTable(master.page, tableName)
    await approve(admin.page, tableName)
    await publishTask(master.page, tableId, `Concepto E2E ${runId}`, 'Para quien quiera postularse')

    await visitor.page.goto(`/tables/${tableId}`)
    await visitor.page.getByRole('button', { name: new RegExp(`Concepto E2E ${runId}`) }).click()

    await expect(visitor.page.getByText('Vas a poder responder esto cuando te postules a la mesa.')).toBeVisible()
    await expect(visitor.page.getByRole('button', { name: 'Entregar', exact: true })).toBeHidden()
  } finally {
    await visitor.context.close()
    await admin.context.close()
    await master.context.close()
  }
})

/**
 * Closing ends the intake and says so where it matters: what was already handed in stays readable,
 * and the request stops appearing among what the table is asking for (#76).
 */
test('closing a request stops new answers and keeps the ones already in', async ({ browser }) => {
  const tableName = `Mesa Cierre E2E ${runId}`
  const playerDiscordId = `e2e-close-player-${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-close-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-close-admin-${runId}`, false, true)
  const player = await newAuthenticatedPage(browser, playerDiscordId, false, false)

  try {
    const tableId = await createTable(master.page, tableName)
    await approve(admin.page, tableName)
    await joinAsPlayer(player.page, master.page, tableId, playerDiscordId)
    await publishTask(master.page, tableId, `Mapa E2E ${runId}`, 'Para los jugadores')

    await player.page.goto(`/my/tables/${tableId}`)
    await player.page.getByRole('button', { name: new RegExp(`Mapa E2E ${runId}`) }).click()
    await player.page.getByRole('button', { name: 'Entregar', exact: true }).click()
    await writeAnswer(player.page, 'Ahi va el mapa')
    await player.page.getByRole('dialog').getByRole('button', { name: 'Entregar' }).click()
    await expect(player.page.getByRole('dialog')).toBeHidden()

    await master.page.goto(`/master/tables/${tableId}/tasks`)
    await master.page.getByRole('button', { name: 'Cerrar la petición' }).click()
    // The confirmation says what survives: closing is not deleting what people sent (#76).
    await expect(master.page.getByRole('dialog').getByText(/sigue estando/)).toBeVisible()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
    await expect(master.page.getByText('Cerrada')).toBeVisible()

    // What was handed in is still readable by the master.
    await master.page.getByRole('button', { name: new RegExp(`Mapa E2E ${runId}`) }).click()
    await expect(master.page.getByText('Ahi va el mapa')).toBeVisible()

    // And the player is no longer being asked for it.
    await player.page.goto(`/my/tables/${tableId}`)
    await expect(player.page.getByRole('button', { name: new RegExp(`Mapa E2E ${runId}`) })).toBeHidden()
  } finally {
    await player.context.close()
    await admin.context.close()
    await master.context.close()
  }
})
