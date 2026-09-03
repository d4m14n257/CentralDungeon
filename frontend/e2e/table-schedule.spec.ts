import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

/**
 * F1.2 de punta a punta, contra el backend real: el wizard completo con agenda, y las dos reglas de
 * #178 que se ven desde la interfaz.
 *
 * Lo que un test unitario no puede probar y esto sí: que la hora que el master escribe en su zona
 * viaja en UTC y vuelve a leerse en su zona (#22), y que un choque se rechaza **con el motivo en
 * pantalla** — el 409 nombrando la mesa con la que se pisa, no un «no se pudo guardar».
 *
 * Login por TestLoginController (perfil `test` del backend), igual que el resto de los specs.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

/** Un viernes a las 20:00 locales, la franja típica de la comunidad. */
const FRIDAY_EVENING = '20:00'

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
 * Recorre los cuatro pasos del wizard poniendo agenda: nombre, duración, una franja y el envío.
 *
 * @param page     la pestaña, ya autenticada como master
 * @param name     el nombre de la mesa
 * @param hourtime la hora local de la franja, `HH:mm`
 */
async function fillWizard(page: Page, name: string, hourtime: string) {
  await page.goto('/master/tables/new')
  await page.getByRole('textbox', { name: 'Nombre' }).fill(name)
  await page.getByRole('button', { name: 'Siguiente' }).click()
  // Paso de catálogos: nada obligatorio, se pasa de largo.
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await page.getByLabel('Duración de una sesión').fill('03:00')
  await page.getByLabel('Hora', { exact: true }).fill(hourtime)
  await page.getByRole('button', { name: 'Agregar' }).click()
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await page.getByRole('button', { name: 'Crear mesa' }).click()
}

/**
 * El escenario central de F1.2: el master arma una mesa con agenda de verdad y la vuelve a ver en su
 * propia hora, con el equivalente UTC a la vista mientras la escribe (#22).
 */
test('a master builds a table with a real weekly agenda through the wizard', async ({ browser }) => {
  const tableName = `Mesa Con Agenda E2E ${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-sched-master-${runId}`, true, false)

  try {
    await master.page.goto('/master/tables/new')
    await master.page.getByRole('textbox', { name: 'Nombre' }).fill(tableName)
    await master.page.getByRole('button', { name: 'Siguiente' }).click()
    await master.page.getByRole('button', { name: 'Siguiente' }).click()

    await master.page.getByLabel('Duración de una sesión').fill('03:00')
    await master.page.getByLabel('Hora', { exact: true }).fill(FRIDAY_EVENING)
    await master.page.getByRole('button', { name: 'Agregar' }).click()

    // La franja se lee en hora local y dice debajo qué se guarda: es la mitad de #22 que se ve.
    await expect(master.page.getByText(`viernes ${FRIDAY_EVENING}`)).toBeVisible()
    await expect(master.page.getByText(/^En UTC:/)).toBeVisible()

    await master.page.getByRole('button', { name: 'Siguiente' }).click()

    // El último paso es el resumen: la agenda aparece antes de mandar nada a revisión.
    await expect(master.page.getByRole('heading', { name: 'Revisión' })).toBeVisible()
    await expect(master.page.getByText(`viernes ${FRIDAY_EVENING}`)).toBeVisible()

    await master.page.getByRole('button', { name: 'Crear mesa' }).click()
    await expect(master.page.getByRole('heading', { name: tableName })).toBeVisible()
  } finally {
    await master.context.close()
  }
})

/**
 * R1 de #178, con el motivo en pantalla: la segunda mesa del mismo master en la misma franja se
 * rechaza con un 409 que **nombra** la mesa con la que se pisa. Un «no se pudo guardar» acá sería
 * exactamente el botón gris sin explicación que el principio 2 de `frontend-diseno.md` prohíbe.
 */
test('a master cannot run two tables in the same slot, and is told which one it clashes with', async ({ browser }) => {
  const firstName = `Mesa Choque A E2E ${runId}`
  const secondName = `Mesa Choque B E2E ${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-clash-master-${runId}`, true, false)

  try {
    await fillWizard(master.page, firstName, FRIDAY_EVENING)
    await expect(master.page.getByRole('heading', { name: firstName })).toBeVisible()

    // La segunda mesa se pisa entera con la primera: misma franja, misma duración.
    await fillWizard(master.page, secondName, FRIDAY_EVENING)

    await expect(master.page.getByText(new RegExp(firstName))).toBeVisible()
    // Y no se creó: el wizard sigue donde estaba, no navegó a la mesa nueva.
    await expect(master.page).toHaveURL(/\/master\/tables\/new$/)
  } finally {
    await master.context.close()
  }
})

/**
 * R2 de #178 desde el lado del jugador: una mesa que se pisa con otra donde ya juega se ve advertida
 * en el explorador y el botón de postularse explica por qué no se puede.
 */
test('a player sees the clash warning and cannot apply to a table that overlaps one they play at', async ({ browser }) => {
  const playerDiscordId = `e2e-clash-player-${runId}`
  const firstTable = `Mesa Jugada E2E ${runId}`
  const secondTable = `Mesa Que Choca E2E ${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-clash-m2-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-clash-admin-${runId}`, false, true)
  const player = await newAuthenticatedPage(browser, playerDiscordId, false, false)

  try {
    // Dos mesas de dos masters distintos en la misma franja: si fueran del mismo, R1 no dejaría.
    await fillWizard(master.page, firstTable, FRIDAY_EVENING)
    await expect(master.page.getByRole('heading', { name: firstTable })).toBeVisible()
    const firstId = master.page.url().split('/master/tables/')[1]

    const otherMaster = await newAuthenticatedPage(browser, `e2e-clash-m3-${runId}`, true, false)
    let secondId: string | undefined
    try {
      await fillWizard(otherMaster.page, secondTable, FRIDAY_EVENING)
      await expect(otherMaster.page.getByRole('heading', { name: secondTable })).toBeVisible()
      secondId = otherMaster.page.url().split('/master/tables/')[1]
    } finally {
      await otherMaster.context.close()
    }

    for (const name of [firstTable, secondTable]) {
      await admin.page.goto('/admin/tables')
      const row = admin.page.getByRole('listitem').filter({ hasText: name })
      await row.getByRole('button', { name: 'Aprobar' }).click()
      await admin.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
      await expect(row).toBeHidden()
    }

    // El jugador entra a la primera y se postula; el master lo acepta.
    await player.page.goto(`/tables/${firstId}`)
    await player.page.getByRole('button', { name: 'Postularme' }).click()
    await player.page.getByRole('dialog').getByRole('button', { name: 'Postularme' }).click()

    await master.page.goto(`/master/tables/${firstId}`)
    const candidate = master.page.getByRole('listitem').filter({ hasText: playerDiscordId }).first()
    await candidate.getByRole('button', { name: 'Aceptar' }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()

    // Ahora la segunda mesa choca: la card lo advierte y el detalle no deja postularse.
    await player.page.goto('/')
    const clashingCard = player.page.getByRole('link').filter({ hasText: secondTable })
    await expect(clashingCard.getByText('Choca con una mesa tuya')).toBeVisible()

    await player.page.goto(`/tables/${secondId}`)
    await expect(player.page.getByRole('button', { name: 'Choca con una mesa tuya' })).toBeDisabled()
    await expect(player.page.getByText(/Ya jugás en otra mesa a esa hora/)).toBeVisible()
  } finally {
    await player.context.close()
    await admin.context.close()
    await master.context.close()
  }
})
