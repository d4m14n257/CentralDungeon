import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

/**
 * F3.1 end to end: the sentence `docs/fase-3-admin-owner.md:110` measures the slice by.
 *
 * > un owner asciende a alguien a admin; ese admin abre `/admin/users`, puede dar el rol de master y
 * > **no encuentra ninguna forma** de dar el de admin; el owner intenta quitarse su propio rol y
 * > recibe el `409`.
 *
 * It is the one check that crosses both halves: the capability rule lives in the frontend
 * (`useUserAdminCapabilities`) and the refusal lives in the backend (`UserRoleService`), and only a
 * run like this one can say that the two agree — that the button an admin does not get is the same
 * action the server would have refused, and that the owner's `409` arrives as a sentence rather than
 * as a toast saying nothing.
 *
 * It also walks `?q=` end to end, which nobody had: the box builds the query, the URL carries it and
 * `SearchQueryParser` on the other side has to read the very same string.
 *
 * Login through TestLoginController (the backend's `test` profile), like every other spec.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

interface Actor {
  context: Awaited<ReturnType<Browser['newContext']>>
  page: Page
  discordId: string
}

/**
 * Signs in without going through Discord.
 *
 * **Careful with the role flags**: test-login leaves the account holding *exactly* the roles asked
 * for, so signing somebody in without a flag takes back what a previous step granted them. That is
 * why the promoted admin below is signed in with `asAdmin` — by then the owner has already granted
 * it through the screen, so the call is a no-op that confirms the state rather than creating it.
 */
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

/** Types a criterion into the people box and closes it into a chip. Enter is what closes one (#240). */
async function search(page: Page, criterion: string) {
  const box = page.getByRole('combobox', { name: 'Buscar usuarios' })
  await box.click()
  await box.fill(criterion)
  await box.press('Enter')
}

/** The row of one account in the wide table, found by its Discord handle. */
function rowOf(page: Page, discordId: string) {
  return page.getByRole('row').filter({ hasText: discordId })
}

/** Opens the role dialog of one account and returns it. */
async function openRoleDialog(page: Page, discordId: string) {
  await rowOf(page, discordId).getByRole('button', { name: 'Cambiar roles' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  return dialog
}

/**
 * The whole `Se prueba:` of F3.1, in one run — because the three sentences are one situation and
 * splitting them would mean promoting somebody three times.
 */
test('an owner promotes an admin, that admin cannot promote anybody, and the owner cannot step down', async ({ browser }) => {
  const ownerId = `e2e-f31-owner-${runId}`
  const futureAdminId = `e2e-f31-newadmin-${runId}`
  const playerId = `e2e-f31-player-${runId}`

  const owner = await newActor(browser, ownerId, { asOwner: true })
  // Both start as plain players; the promotion below is what makes one of them an admin.
  const futureAdmin = await newActor(browser, futureAdminId)
  const player = await newActor(browser, playerId)

  try {
    // ---- 1. The owner promotes somebody to Admin, from the screen.
    await owner.page.goto('/admin/users')
    await expect(owner.page.getByRole('heading', { name: 'Usuarios' })).toBeVisible()
    await search(owner.page, `/discord_name ${futureAdminId}`)

    const dialog = await openRoleDialog(owner.page, futureAdminId)
    // The owner sees all four ranks. This is the assertion a `hasRole('ADMIN')` would not reach.
    for (const role of ['Jugador', 'Master', 'Admin', 'Owner']) {
      await expect(dialog.getByRole('button', { name: new RegExp(`^${role}\\b`) })).toBeVisible()
    }
    await dialog.getByRole('button', { name: /^Admin\b/ }).click()
    await dialog.getByLabel('Motivo').fill('Promoción de prueba E2E de F3.1')
    await dialog.getByRole('button', { name: 'Dar el rol' }).click()
    await expect(dialog).toBeHidden()

    // Longer than the default: what is being asserted is that the listing refreshes itself after a
    // grant - no reload, no second search - and that round trip is an invalidation plus a refetch.
    // On a loaded machine (the suite runs six workers) five seconds is not always enough for it, and
    // shortening the wait would turn a timing budget into a flake.
    await expect(rowOf(owner.page, futureAdminId)).toContainText('Admin', { timeout: 15_000 })

    // ---- 2. The audit trail is readable, which is what keeps the two tables from being write-only.
    await rowOf(owner.page, futureAdminId).getByRole('button', { name: 'Historial' }).click()
    const history = owner.page.getByRole('dialog')
    await expect(history).toContainText('Promoción de prueba E2E de F3.1')
    await expect(history).toContainText(ownerId)
    await history.getByRole('button', { name: 'Close' }).click()
    await expect(history).toBeHidden()

    // ---- 3. That admin opens /admin/users. The sign-in is a no-op: step 1 already granted Admin.
    await testLogin(futureAdmin.context.request, futureAdminId, { asAdmin: true })
    await futureAdmin.page.goto('/admin/users')
    await expect(futureAdmin.page.getByRole('heading', { name: 'Usuarios' })).toBeVisible()
    await search(futureAdmin.page, `/discord_name ${playerId}`)

    const adminDialog = await openRoleDialog(futureAdmin.page, playerId)
    // Master, yes.
    await expect(adminDialog.getByRole('button', { name: /^Master\b/ })).toBeVisible()
    await expect(adminDialog.getByRole('button', { name: /^Jugador\b/ })).toBeVisible()
    // Admin and Owner: **no way at all**. Not disabled - absent (principio 2 de frontend-diseno.md §1).
    await expect(adminDialog.getByRole('button', { name: /^Admin\b/ })).toHaveCount(0)
    await expect(adminDialog.getByRole('button', { name: /^Owner\b/ })).toHaveCount(0)

    // And what they may do, they really may: the grant goes through.
    await adminDialog.getByRole('button', { name: /^Master\b/ }).click()
    await adminDialog.getByLabel('Motivo').fill('Va a dirigir una mesa')
    await adminDialog.getByRole('button', { name: 'Dar el rol' }).click()
    await expect(adminDialog).toBeHidden()
    await expect(rowOf(futureAdmin.page, playerId)).toContainText('Master', { timeout: 15_000 })

    // ---- 4. The owner cannot take their own Owner away: the 409, said in words.
    await owner.page.goto('/admin/users')
    await search(owner.page, `/discord_name ${ownerId}`)
    const ownOwnDialog = await openRoleDialog(owner.page, ownerId)
    await ownOwnDialog.getByRole('button', { name: /^Owner\b/ }).click()
    // The dialog says what the press will do before it is pressed.
    await expect(ownOwnDialog).toContainText('Se le va a quitar el rol Owner')
    await ownOwnDialog.getByLabel('Motivo').fill('Intento de renuncia')
    await ownOwnDialog.getByRole('button', { name: 'Quitar el rol' }).click()

    // The refusal is rendered from the error code, not from the backend's English detail (#197).
    await expect(ownOwnDialog.getByRole('alert')).toContainText('No podés quitarte tu propio rol de Owner')
    // And the dialog stays open with the account untouched: nothing was half-applied.
    await expect(ownOwnDialog).toBeVisible()
  } finally {
    await owner.context.close()
    await futureAdmin.context.close()
    await player.context.close()
  }
})

/**
 * `?q=` across the two parsers. The box builds the canonical string, the URL carries it and
 * `SearchQueryParser` on the backend reads the same one - the correlated EXISTS of `/role` included.
 * Both sides declared this without ever running it against each other.
 */
test('the /role command travels from the box through the URL to the backend', async ({ browser }) => {
  // The run's marker goes before the role, so that one `/discord_name` finds the three of them and
  // nobody else: it has to be a real substring of every handle, not a suffix they happen to share.
  const prefix = `e2e-f31q-${runId}`
  const ownerId = `${prefix}-owner`
  const masterId = `${prefix}-master`
  const playerId = `${prefix}-player`

  const owner = await newActor(browser, ownerId, { asOwner: true })
  const master = await newActor(browser, masterId, { asMaster: true })
  const player = await newActor(browser, playerId)

  try {
    await owner.page.goto('/admin/users')
    await search(owner.page, `/discord_name ${prefix}`)
    await expect(rowOf(owner.page, masterId)).toBeVisible()
    await expect(rowOf(owner.page, playerId)).toBeVisible()

    // Narrowing by a live role: the master stays, the player goes. Reloading first because the box
    // keeps what was already closed into a chip, and this criterion replaces it rather than joining
    // it - the same shape table-search.spec.ts uses between two searches.
    await owner.page.reload()
    await search(owner.page, `/discord_name ${prefix} /and /role Master`)

    // The canonical string the box built is the one the address bar carries and the one
    // SearchQueryParser reads on the other side (#185, #240).
    await expect(owner.page).toHaveURL(/q=%2Frole\+Master|q=.*role.*Master/)
    await expect(rowOf(owner.page, masterId)).toBeVisible()
    await expect(rowOf(owner.page, playerId)).toHaveCount(0)

    // The state is in the URL, so a reload has to come back to the same rows (#185).
    await owner.page.reload()
    await expect(rowOf(owner.page, masterId)).toBeVisible()
    await expect(rowOf(owner.page, playerId)).toHaveCount(0)

    // A role nobody has is an empty result, never a 400 (§2.5).
    await owner.page.goto(`/admin/users?q=${encodeURIComponent(`/discord_name ${masterId} /and /role Owner`)}`)
    await expect(owner.page.getByText('Nadie coincide con esa búsqueda')).toBeVisible()

    // And a role name outside the four is text that finds nobody, not a 400 either.
    await owner.page.goto(`/admin/users?q=${encodeURIComponent('/role Wizard')}`)
    await expect(owner.page.getByText('Nadie coincide con esa búsqueda')).toBeVisible()
  } finally {
    await owner.context.close()
    await master.context.close()
    await player.context.close()
  }
})

/**
 * The other half of the matrix: a block is the door closing, and the accounts holding a rank are out
 * of reach of it - which the screen expresses by not offering the button at all (#84, §3).
 */
test('an account can be blocked and let back in, and one holding a rank is never offered a block', async ({ browser }) => {
  // Same reason as above: the marker before the role, so one search brings the three of them up.
  const prefix = `e2e-f31b-${runId}`
  const ownerId = `${prefix}-owner`
  const victimId = `${prefix}-target`
  const adminId = `${prefix}-admin`

  const owner = await newActor(browser, ownerId, { asOwner: true })
  const victim = await newActor(browser, victimId)
  const admin = await newActor(browser, adminId, { asAdmin: true })

  try {
    // The account is in before anything happens, so the assertion after the block is about the block.
    await victim.page.goto('/')
    await expect(victim.page).not.toHaveURL(/\/login/)

    await owner.page.goto('/admin/users')
    await search(owner.page, `/discord_name ${prefix}`)
    // Wait for the narrowed listing before asking what is missing from it: an assertion about an
    // absent button is satisfied by a table that has not caught up yet.
    await expect(rowOf(owner.page, victimId).getByRole('button', { name: 'Bloquear' })).toBeVisible()

    // Nobody blocks a privileged account - not the other admin, and not the owner themselves.
    await expect(rowOf(owner.page, adminId).getByRole('button', { name: 'Bloquear' })).toHaveCount(0)
    await expect(rowOf(owner.page, ownerId).getByRole('button', { name: 'Bloquear' })).toHaveCount(0)

    await rowOf(owner.page, victimId).getByRole('button', { name: 'Bloquear' }).click()
    const blockDialog = owner.page.getByRole('dialog')
    // The dialog says what a block implies before it is confirmed (#84).
    await expect(blockDialog).toContainText('no va a poder volver a entrar')
    await expect(blockDialog).toContainText('Sus datos se conservan')
    await blockDialog.getByLabel('Motivo').fill('Prueba E2E de bloqueo')
    await blockDialog.getByRole('button', { name: 'Bloquear' }).click()
    await expect(blockDialog).toBeHidden()

    await expect(rowOf(owner.page, victimId)).toContainText('Bloqueada', { timeout: 15_000 })

    // The block applies now and not at the end of the cache's TTL (#128): the blocked person's own
    // tab stops working on its very next load. `AuthService.refresh` re-reads the snapshot and
    // refuses anything that is not Allowed, so a cache that had not been dropped would have kept
    // them inside for another minute - "a block that takes sixty seconds to apply is a block that
    // does not block".
    await victim.page.goto('/')
    await expect(victim.page).toHaveURL(/\/login/)

    // And letting them back in is the same door, the other way.
    await rowOf(owner.page, victimId).getByRole('button', { name: 'Desbloquear' }).click()
    const unblockDialog = owner.page.getByRole('dialog')
    await unblockDialog.getByLabel('Motivo').fill('Se aclaró el malentendido')
    await unblockDialog.getByRole('button', { name: 'Desbloquear' }).click()
    await expect(unblockDialog).toBeHidden()
    await expect(rowOf(owner.page, victimId)).toContainText('Activa', { timeout: 15_000 })
  } finally {
    await owner.context.close()
    await victim.context.close()
    await admin.context.close()
  }
})
