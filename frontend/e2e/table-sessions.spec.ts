import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

/**
 * F1.3 de punta a punta, contra el backend real: el calendario que se materializa al abrir la mesa,
 * las cuatro cosas que un master hace con él, y lo que ve el jugador en `/my/tables/:id`.
 *
 * Lo que ningún test unitario prueba y esto sí: que aprobar una mesa **crea las sesiones de verdad**
 * a partir de la agenda, que cancelar una repone otra al final (#194), y que la asistencia que el
 * master registra es la que el jugador ve, con los tres números de #137 y no con un porcentaje.
 *
 * Login por TestLoginController (perfil `test` del backend), igual que el resto de los specs.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

/** Un viernes a las 20:00 locales, la franja típica de la comunidad. */
const FRIDAY_EVENING = '20:00'

/** Cuatro sesiones: suficiente para ver la numeración y la reposición sin llenar la pantalla. */
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
 * Arma una mesa completa: nombre, fecha de inicio, duración, una franja semanal y la cantidad de
 * sesiones. Los tres últimos son lo que la materialización necesita (#26, #33).
 *
 * @param page la pestaña, ya autenticada como master
 * @param name el nombre de la mesa
 * @returns el id de la mesa creada
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

/** Aprueba la mesa desde `/admin/tables`, que es lo que dispara la materialización. */
async function approve(page: Page, name: string) {
  await page.goto('/admin/tables')
  const row = page.getByRole('listitem').filter({ hasText: name })
  await row.getByRole('button', { name: 'Aprobar' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
  await expect(row).toBeHidden()
}

/**
 * El escenario central: abrir la mesa crea las cuatro sesiones, y el master las corrige, las cierra
 * y las cancela desde la pestaña.
 */
test('opening a table materializes its calendar, and the master can run it', async ({ browser }) => {
  const tableName = `Mesa Sesiones E2E ${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-sess-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-sess-admin-${runId}`, false, true)

  try {
    const tableId = await createTableWithCalendar(master.page, tableName)
    await approve(admin.page, tableName)

    await master.page.goto(`/master/tables/${tableId}/sessions`)

    // Las cuatro sesiones existen, numeradas, sin que nadie las haya escrito a mano.
    await expect(master.page.getByRole('button', { name: /^Sesión 1/ })).toBeVisible()
    await expect(master.page.getByRole('button', { name: /^Sesión 4/ })).toBeVisible()

    // Marcar jugada es una acción propia, separada de la asistencia (#195).
    await master.page.getByRole('button', { name: 'Marcar como jugada' }).first().click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
    // `exact`: el toast de confirmación dice «Sesión marcada como jugada» y también contiene la palabra.
    await expect(master.page.getByText('Jugada', { exact: true })).toBeVisible()
  } finally {
    await admin.context.close()
    await master.context.close()
  }
})

/**
 * #194 con el aviso antes de confirmar: cancelar una sesión repone otra al final, y la interfaz lo
 * dice **antes** de que el master apriete, no después de que aparezca una sesión que no esperaba.
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
    // El motivo está en el diálogo, antes de confirmar: la mesa sigue jugando la misma cantidad.
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
 * El otro lado: el master registra la asistencia y el jugador la ve en `/my/tables/:id`, con los
 * tres números de #137 — nunca un porcentaje, que escondería la distinción que importa.
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
    // La ficha pública ya muestra el calendario real, no solo la forma semanal.
    await expect(player.page.getByText('Sesión 1')).toBeVisible()
    await player.page.getByRole('button', { name: 'Postularme' }).click()
    await player.page.getByRole('dialog').getByRole('button', { name: 'Postularme' }).click()

    await master.page.goto(`/master/tables/${tableId}`)
    const candidate = master.page.getByRole('listitem').filter({ hasText: playerDiscordId }).first()
    await candidate.getByRole('button', { name: 'Aceptar' }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()

    await master.page.goto(`/master/tables/${tableId}/sessions`)
    await master.page.getByRole('button', { name: /^Sesión 1/ }).click()
    // El Select de shadcn no es un <select> nativo: se abre y se elige la opción de la lista.
    await master.page.getByRole('combobox', { name: /^Asistencia de/ }).click()
    await master.page.getByRole('option', { name: 'Presente' }).click()
    await master.page.getByRole('button', { name: 'Guardar asistencia' }).click()

    await player.page.goto(`/my/tables/${tableId}`)
    await expect(player.page.getByRole('heading', { name: tableName })).toBeVisible()
    await expect(player.page.getByText('Mis sesiones')).toBeVisible()
    // El resumen, no la línea de la sesión: los dos dicen «Presente» y son cosas distintas.
    await expect(player.page.getByRole('term').filter({ hasText: 'Presente' })).toBeVisible()
    // Tres números y su denominador; nunca un «1 de 4» ni un porcentaje (#137).
    await expect(player.page.getByText('Sesiones registradas')).toBeVisible()
    await expect(player.page.getByText('%')).toBeHidden()
  } finally {
    await player.context.close()
    await admin.context.close()
    await master.context.close()
  }
})
