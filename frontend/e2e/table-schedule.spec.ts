import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

/**
 * F1.2 end to end, against the real backend: the complete wizard with an agenda, and the two rules
 * of #178 that can be seen from the interface.
 *
 * What no unit test can prove and this does: that the time a master types in their own zone travels
 * as UTC and is read back in their zone (#22), and that a clash is refused **with the reason on
 * screen** — the 409 naming the table it collides with, not a "could not save".
 *
 * Login through TestLoginController (the backend's `test` profile), like every other spec.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

/** A Friday at 20:00 local time, the community's usual slot. */
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
 * Walks the wizard's four steps filling in an agenda: name, duration, one slot and the submit.
 *
 * @param page     the tab, already signed in as a master
 * @param name     the table's name
 * @param hourtime the slot's local time, `HH:mm`
 */
async function fillWizard(page: Page, name: string, hourtime: string) {
  await page.goto('/master/tables/new')
  await page.getByRole('textbox', { name: 'Nombre' }).fill(name)
  await page.getByRole('button', { name: 'Siguiente' }).click()
  // The catalogs step: nothing is required, so it is walked past.
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await page.getByLabel('Duración de una sesión').fill('03:00')
  await page.getByLabel('Hora', { exact: true }).fill(hourtime)
  await page.getByRole('button', { name: 'Agregar' }).click()
  await page.getByRole('button', { name: 'Siguiente' }).click()

  await page.getByRole('button', { name: 'Crear mesa' }).click()
}

/**
 * F1.2's central scenario: the master builds a table with a real agenda and reads it back in their
 * own time, with the UTC equivalent in view while they type it (#22).
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

    // The slot reads in local time and says underneath what gets stored: it is the half of #22 that shows.
    await expect(master.page.getByText(`viernes ${FRIDAY_EVENING}`)).toBeVisible()
    await expect(master.page.getByText(/^En UTC:/)).toBeVisible()

    await master.page.getByRole('button', { name: 'Siguiente' }).click()

    // The last step is the summary: the agenda shows before anything is sent for review.
    await expect(master.page.getByRole('heading', { name: 'Revisión' })).toBeVisible()
    await expect(master.page.getByText(`viernes ${FRIDAY_EVENING}`)).toBeVisible()

    await master.page.getByRole('button', { name: 'Crear mesa' }).click()
    await expect(master.page.getByRole('heading', { name: tableName })).toBeVisible()
  } finally {
    await master.context.close()
  }
})

/**
 * R1 of #178, with the reason on screen: the same master's second table in the same slot is refused
 * with a 409 that **names** the table it collides with. A "could not save" here would be exactly the
 * unexplained grey button that principio 2 of `frontend-diseno.md` forbids.
 */
test('a master cannot run two tables in the same slot, and is told which one it clashes with', async ({ browser }) => {
  const firstName = `Mesa Choque A E2E ${runId}`
  const secondName = `Mesa Choque B E2E ${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-clash-master-${runId}`, true, false)

  try {
    await fillWizard(master.page, firstName, FRIDAY_EVENING)
    await expect(master.page.getByRole('heading', { name: firstName })).toBeVisible()

    // The second table overlaps the first entirely: same slot, same duration.
    await fillWizard(master.page, secondName, FRIDAY_EVENING)

    await expect(master.page.getByText(new RegExp(firstName))).toBeVisible()
    // And it was not created: the wizard is where it was, it did not navigate to a new table.
    await expect(master.page).toHaveURL(/\/master\/tables\/new$/)
  } finally {
    await master.context.close()
  }
})

/**
 * R2 of #178 from the player's side: a table that overlaps one they already play at shows warned in
 * the explorer, and the apply button explains why it cannot be used.
 */
test('a player sees the clash warning and cannot apply to a table that overlaps one they play at', async ({ browser }) => {
  const playerDiscordId = `e2e-clash-player-${runId}`
  const firstTable = `Mesa Jugada E2E ${runId}`
  const secondTable = `Mesa Que Choca E2E ${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-clash-m2-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-clash-admin-${runId}`, false, true)
  const player = await newAuthenticatedPage(browser, playerDiscordId, false, false)

  try {
    // Two tables from two different masters in the same slot: from the same one, R1 would refuse.
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

    // The player opens the first one and applies; the master accepts them.
    await player.page.goto(`/tables/${firstId}`)
    await player.page.getByRole('button', { name: 'Postularme' }).click()
    await player.page.getByRole('dialog').getByRole('button', { name: 'Postularme' }).click()

    await master.page.goto(`/master/tables/${firstId}`)
    const candidate = master.page.getByRole('listitem').filter({ hasText: playerDiscordId }).first()
    await candidate.getByRole('button', { name: 'Aceptar' }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()

    // Now the second table clashes: the card warns about it and the detail does not allow applying.
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
