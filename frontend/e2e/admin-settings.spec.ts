import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

/**
 * F3.5 end to end: the sentence `docs/fase-3-admin-owner.md` measures the slice by.
 *
 * > un admin cambia el tope por archivo; la subida siguiente lo respeta sin reiniciar nada, y el
 * > cambio queda registrado con quién y cuándo.
 *
 * **It is the one check that crosses both halves, and the crossing is the whole point.** The number
 * lives in `system_settings`, the backend reads it through a cached accessor, and a *different*
 * screen in a *different* feature refuses files against it. A unit test on either side can only say
 * that each half does what it was told; only a run like this one can say that changing the value in
 * `/admin/settings` is what makes `/my/files` state a different limit — with nothing restarted and
 * no page told to go and look.
 *
 * **On the settings being global.** Every other spec here works on rows it created; this one changes
 * a platform-wide value, so it puts it back before it finishes. The teardown is the second net: the
 * override row points at the `e2e-*` account that wrote it, so `TestDataService` deletes it and the
 * setting falls back to `SettingKey`'s default even if this run dies halfway.
 *
 * Login through TestLoginController (the backend's `test` profile), like every other spec.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

/** What the platform ships with, and what this spec has to leave behind. */
const DEFAULT_FILE_CAP_MB = 2

interface Actor {
  context: Awaited<ReturnType<Browser['newContext']>>
  page: Page
  discordId: string
}

async function testLogin(
  request: APIRequestContext,
  discordId: string,
  roles: { asMaster?: boolean; asAdmin?: boolean; asOwner?: boolean } = {},
) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: {
      discordId,
      asMaster: roles.asMaster ?? false,
      asAdmin: roles.asAdmin ?? false,
      asOwner: roles.asOwner ?? false,
    },
  })
  expect(response.ok()).toBeTruthy()
}

async function newActor(
  browser: Browser,
  discordId: string,
  roles: { asMaster?: boolean; asAdmin?: boolean; asOwner?: boolean } = {},
): Promise<Actor> {
  const context = await browser.newContext()
  await testLogin(context.request, discordId, roles)
  const page = await context.newPage()
  return { context, page, discordId }
}

/**
 * The card of one setting, found by the label the screen renders for it.
 *
 * Scoped to the `main` landmark and not to any `listitem`: sonner renders each toast as a list item
 * inside a `<section>` of its own, and the success toast names the setting that was just saved — so
 * both a bare role and a `section li` matched two elements the moment a change went through.
 */
function cardOf(page: Page, label: string) {
  return page.getByRole('main').getByRole('listitem').filter({ hasText: label })
}

/** Opens a setting's editor, types a value and a reason, and saves. */
async function changeSetting(page: Page, label: string, value: string, reason: string) {
  await cardOf(page, label).getByRole('button', { name: 'Cambiar' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await dialog.getByLabel(/Nuevo valor/).fill(value)
  await dialog.getByLabel('Motivo').fill(reason)
  await dialog.getByRole('button', { name: 'Guardar el cambio' }).click()
  return dialog
}

/**
 * The whole `Se prueba:` of F3.5 in one run, because the sentences are one situation: a value is
 * changed, a different screen respects it, and the change left a record.
 */
test('an admin raises the file cap, the next upload screen respects it, and the change is on the record', async ({ browser }) => {
  const adminId = `e2e-f35-admin-${runId}`
  const playerId = `e2e-f35-player-${runId}`

  const admin = await newActor(browser, adminId, { asAdmin: true })
  const player = await newActor(browser, playerId)

  try {
    // ---- 1. The screen opens on what is in force, beside what the platform ships with.
    await admin.page.goto('/admin/settings')
    await expect(admin.page.getByRole('heading', { name: 'Configuración' })).toBeVisible()

    const fileCap = cardOf(admin.page, 'Tope por archivo')
    await expect(fileCap).toContainText(`${DEFAULT_FILE_CAP_MB} MB`)
    await expect(fileCap).toContainText(`Por defecto: ${DEFAULT_FILE_CAP_MB}`)

    // ---- 2. The other side, *before* the change. Asserting it first is what makes step 4 mean
    //         something: without it, "Hasta 5 MB" could have been the copy all along.
    await player.page.goto('/my/files')
    await player.page.getByRole('button', { name: 'Subir archivo' }).click()
    await expect(player.page.getByText(`Hasta ${DEFAULT_FILE_CAP_MB} MB por archivo`, { exact: false })).toBeVisible()

    // ---- 3. The range is refused by the screen, in the reader's language and naming both bounds.
    await cardOf(admin.page, 'Tope por archivo').getByRole('button', { name: 'Cambiar' }).click()
    const refusing = admin.page.getByRole('dialog')
    await refusing.getByLabel(/Nuevo valor/).fill('500')
    await refusing.getByLabel('Motivo').fill('Fuera de rango a propósito')
    await refusing.getByRole('button', { name: 'Guardar el cambio' }).click()
    await expect(refusing.getByText('Tiene que estar entre 1 y 25')).toBeVisible()
    // Closing a half-filled form asks first (#110), which is `FormDialog` doing its job and not
    // something this dialog had to remember. Confirming the discard is part of the path.
    await refusing.getByRole('button', { name: 'Close' }).click()
    await admin.page.getByRole('button', { name: 'Descartar lo hecho' }).click()
    await expect(refusing).toBeHidden()

    // ---- 4. The change itself.
    const dialog = await changeSetting(admin.page, 'Tope por archivo', '5', 'Los mapas pesan más de lo que pensábamos')
    await expect(dialog).toBeHidden()
    await expect(cardOf(admin.page, 'Tope por archivo')).toContainText('5 MB', { timeout: 15_000 })
    // Who has it like this, which is the half `system_settings` answers on its own.
    await expect(cardOf(admin.page, 'Tope por archivo')).toContainText(adminId)

    // ---- 5. **The crossing.** A different person, a different screen, a different feature - and no
    //         restart anywhere. The dropzone asks the server for the cap and states the new number.
    await player.page.reload()
    await player.page.getByRole('button', { name: 'Subir archivo' }).click()
    await expect(player.page.getByText('Hasta 5 MB por archivo', { exact: false })).toBeVisible()

    // ---- 6. The record: from what, to what, by whom and why. It is what keeps
    //         `system_setting_changes` from being write-only.
    await cardOf(admin.page, 'Tope por archivo').getByRole('button', { name: 'Historial' }).click()
    const history = admin.page.getByRole('dialog')
    await expect(history).toContainText('Los mapas pesan más de lo que pensábamos')
    await expect(history).toContainText(adminId)
    // The first change says it replaced the shipped default, which is a different fact from
    // "it was already 2".
    await expect(history).toContainText('sobre el valor de fábrica')
    await history.getByRole('button', { name: 'Close' }).click()
    await expect(history).toBeHidden()

    // ---- 7. And a second change records what it replaced, which is what makes a value raised and
    //         put back readable as one story. It is also how this spec cleans up after itself.
    const restoring = await changeSetting(
      admin.page,
      'Tope por archivo',
      String(DEFAULT_FILE_CAP_MB),
      'Volvemos al valor de fábrica al terminar la prueba',
    )
    await expect(restoring).toBeHidden()
    await expect(cardOf(admin.page, 'Tope por archivo')).toContainText(`${DEFAULT_FILE_CAP_MB} MB`, { timeout: 15_000 })

    await cardOf(admin.page, 'Tope por archivo').getByRole('button', { name: 'Historial' }).click()
    const secondHistory = admin.page.getByRole('dialog')
    await expect(secondHistory).toContainText(`Pasó de 5 a ${DEFAULT_FILE_CAP_MB}`)
  } finally {
    await admin.context.close()
    await player.context.close()
  }
})

/**
 * The row of the matrix, in the browser: editing settings is a capability `Admin` and `Owner` share
 * (`fase-3-admin-owner.md` §3), so an owner finds the same screen with the same buttons.
 *
 * It is the display half of what `AdminSettingsApiIT` asserts over HTTP. A `hasRole('ADMIN')` would
 * be caught there; what this catches is the other shape of the same bug - a screen that hides a
 * button the server would have accepted.
 */
test('an owner finds the same settings screen an admin does', async ({ browser }) => {
  const ownerId = `e2e-f35-owner-${runId}`
  const owner = await newActor(browser, ownerId, { asOwner: true })

  try {
    await owner.page.goto('/admin/settings')
    await expect(owner.page.getByRole('heading', { name: 'Configuración' })).toBeVisible()
    await expect(cardOf(owner.page, 'Tope por archivo').getByRole('button', { name: 'Cambiar' })).toBeVisible()
    await expect(cardOf(owner.page, 'Cupo máximo por mesa').getByRole('button', { name: 'Cambiar' })).toBeVisible()
  } finally {
    await owner.context.close()
  }
})

/**
 * The warning of #141, on the card and before anything is opened.
 *
 * The visibility window is the one setting of this slice whose change is retroactive: it is evaluated
 * on every profile read, so lowering it takes visibility away from people who have it at that
 * instant. #141 asks for the warning by name, and a warning that only appears after saving is not
 * one.
 */
test('the retroactive setting says so before anybody opens it', async ({ browser }) => {
  const adminId = `e2e-f35-warn-${runId}`
  const admin = await newActor(browser, adminId, { asAdmin: true })

  try {
    await admin.page.goto('/admin/settings')
    const visibility = cardOf(admin.page, 'Caducidad de la visibilidad de perfiles')
    await expect(visibility).toContainText(/este cambio no espera/i)
    // And the three that are not retroactive say nothing of the sort, so the warning keeps meaning
    // something.
    await expect(cardOf(admin.page, 'Tope por archivo')).not.toContainText(/este cambio no espera/i)
  } finally {
    await admin.context.close()
  }
})
