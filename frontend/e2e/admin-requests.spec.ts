import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

/**
 * F3.2 end to end: the sentence `docs/fase-3-admin-owner.md:128` measures the slice by.
 *
 * > un jugador pide el rol de master; el pedido aparece en `/admin/requests` con su motivo; un admin
 * > lo aprueba y **el rol queda otorgado por el `UserRoleService` de F3.1, no por una segunda ruta
 * > que haga lo mismo**.
 *
 * That last clause is the one a backend test cannot see from outside and a frontend test cannot see
 * at all. What proves it here is the audit trail: `UserRoleService` is what writes
 * `user_role_changes`, so if the row shows up in `/admin/users` → Historial with the admin's
 * resolution note as its justification, the grant went through that service and not through a second
 * path that wrote `users_roles` on its own.
 *
 * Three more things only a run like this can answer, and none of them had ever been exercised:
 *
 * - **The button is not offered while a request of that kind is pending** (principio 2 de
 *   `frontend-diseno.md` §1). The rule just changed shape: the hook now asks for
 *   `?q=/status Pending` instead of reading page one, so the string the frontend builds has to be
 *   the string `SearchQueryParser` reads on the other side. That agreement was the fragile point of
 *   F3.1 and there is no test on either side that can check it alone.
 * - **The two new notifications reach the requester and open something.** The guard in
 *   `notificationTarget` was rewritten for them, and nobody had ever rendered one from a real row.
 * - **Approving a `TableOpen` creates no table.** It is the rule of this slice most easily
 *   implemented one step too far.
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
 * for, so signing somebody in without a flag takes back what a previous step granted them. The
 * requester below is never signed in again after the approval, for that reason.
 */
async function testLogin(
  request: APIRequestContext,
  discordId: string,
  roles: { asMaster?: boolean; asAdmin?: boolean; asOwner?: boolean } = {},
): Promise<string> {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: {
      discordId,
      asMaster: roles.asMaster ?? false,
      asAdmin: roles.asAdmin ?? false,
      asOwner: roles.asOwner ?? false,
    },
  })
  expect(response.ok()).toBeTruthy()
  // The same access token the application would hold in memory. It is returned rather than dropped
  // because one assertion below has to ask the API a question no screen answers.
  return ((await response.json()) as { accessToken: string }).accessToken
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

/** Types a criterion into a search box and closes it into a chip. Enter is what closes one (#240). */
async function search(page: Page, label: string, criterion: string) {
  const box = page.getByRole('combobox', { name: label })
  await box.click()
  await box.fill(criterion)
  await box.press('Enter')
}

/** The row of one request in the wide table, found by the handle of whoever asked. */
function requestRow(page: Page, discordId: string) {
  return page.getByRole('row').filter({ hasText: discordId })
}

/**
 * Raises a request from the screen that prompts it, and returns once it is gone from the screen -
 * which is the section swapping the button for the pending notice.
 */
async function ask(page: Page, buttonName: string, justification: string) {
  await page.getByRole('button', { name: buttonName }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await dialog.getByLabel('Motivo').fill(justification)
  await dialog.getByRole('button', { name: 'Enviar el pedido' }).click()
  await expect(dialog).toBeHidden()
}

/**
 * The ids of every table sitting in `Unassigned`, asked of the API rather than counted off a screen.
 *
 * No screen answers "did a table appear anywhere", which is exactly the question here: a table born
 * from an approval would be `Unassigned` (#72, the status a table with no master yet gets), and no
 * public listing shows those.
 *
 * The token is minted by signing the admin in again with the flags they already hold - test-login
 * sets the roles exactly, so for an account that is already an admin the call is a no-op that
 * returns a usable token. The application's own token lives in its memory and is not reachable from
 * here.
 *
 * <p>Approving a {@code TableOpen} must create nothing; the table is the admin's own next step
 * (#72), and one born from an approval would land here, which is the status #72 gives a table with
 * no master yet.
 *
 * <p><b>Why ids of one status and not the total count.</b> The obvious assertion - total tables
 * before equals total tables after - is not assertable with parallel workers: the suite runs six at
 * a time and any other spec creating a table between the two reads fails this one for something it
 * did not do. Narrowing to {@code Unassigned} leaves a single spec able to interfere instead of
 * fifteen, and comparing ids rather than counts means an interfering table shows up as a named
 * stranger rather than as an off-by-one.
 *
 * <p>The honest scoped version - asking the listing for this run's marker - needs {@code ?q=} on
 * {@code /game-tables/admin}, which **F3.3 adds** (#176). Tighten this then.
 */
async function unassignedTableIds(actor: Actor): Promise<Set<string>> {
  const accessToken = await testLogin(actor.context.request, actor.discordId, { asAdmin: true })
  const tables = await actor.context.request.get(`${BACKEND_URL}/api/v1/game-tables/admin?status=Unassigned&size=100`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  expect(tables.ok()).toBeTruthy()
  const page = (await tables.json()) as { content: { id: string }[] }
  return new Set(page.content.map((table) => table.id))
}

/**
 * The whole `Se prueba:` of F3.2 in one run, because the four sentences are one situation: the same
 * request has to be asked for, seen, approved and felt, and splitting it would mean asking four
 * times.
 */
test('a player asks for the master role, an admin approves it from /admin/requests, and the role is granted by UserRoleService', async ({
  browser,
}) => {
  const adminId = `e2e-f32-admin-${runId}`
  const playerId = `e2e-f32-player-${runId}`
  const justification = `Quiero dirigir una mesa de terror (E2E ${runId})`
  const resolutionNote = `Tiene experiencia, adelante (E2E ${runId})`

  const admin = await newActor(browser, adminId, { asAdmin: true })
  const player = await newActor(browser, playerId)

  try {
    // ---- 1. The request is raised from the screen that provokes it, not from a "make a request"
    // screen (fase-3-admin-owner.md:126). For the master role that screen is the reader's own profile.
    await player.page.goto('/player/profile')
    await expect(player.page.getByText('¿Querés dirigir mesas?')).toBeVisible()
    await ask(player.page, 'Pedir el rol de master', justification)

    // ---- 2. **The button is gone and the pending request is shown instead** (principio 2).
    //
    // This is the assertion that crosses the two `?q=` implementations: the section asks the backend
    // for `/status Pending`, a string the frontend builds from its own field list, and
    // `SearchQueryParser` on the other side has to read that exact string. If the two disagree the
    // answer comes back empty, the button is drawn again, and its only possible reply is a 409.
    await expect(player.page.getByText('Ya pediste el rol de master y todavía no te respondieron.')).toBeVisible()
    await expect(player.page.getByRole('button', { name: 'Pedir el rol de master' })).toHaveCount(0)

    // And it survives a reload, because the answer comes from the server and not from a mutation
    // that happened to be on screen.
    await player.page.reload()
    await expect(player.page.getByText('Ya pediste el rol de master y todavía no te respondieron.')).toBeVisible()
    await expect(player.page.getByRole('button', { name: 'Pedir el rol de master' })).toHaveCount(0)

    // ---- 3. The request reaches /admin/requests, with its motivo.
    // Through the URL and not by typing: the box opens holding the `Pending` chip, and a criterion
    // typed into it *joins* what is already there rather than replacing it (#240). A `?q=` in the
    // address is the screen's own state, and it is also what proves the string travels
    // (#185) - the box builds it, the URL carries it, `SearchQueryParser` reads it.
    await admin.page.goto(`/admin/requests?q=${encodeURIComponent(`/status Pending /and /requested_by ${playerId}`)}`)
    await expect(admin.page.getByRole('heading', { name: 'Pedidos' })).toBeVisible()

    const row = requestRow(admin.page, playerId)
    await expect(row).toBeVisible()
    await expect(row).toContainText('Rol de master')
    await expect(row).toContainText('Pendiente')
    // With its motivo: the whole of what the admin has to decide on.
    await expect(row).toContainText(justification)

    // ---- 4. The admin approves it, with a reason of their own - mandatory at both ends (#42).
    await row.getByRole('button', { name: 'Aprobar' }).click()
    const approveDialog = admin.page.getByRole('dialog')
    await expect(approveDialog).toBeVisible()
    // The dialog says what approving *this* type does before it is pressed: it grants the role by
    // the same path as the Usuarios screen.
    await expect(approveDialog).toContainText('recibe el rol de master por el mismo camino')
    await approveDialog.getByLabel('Tu respuesta').fill(resolutionNote)
    await approveDialog.getByRole('button', { name: 'Aprobar' }).click()
    await expect(approveDialog).toBeHidden()

    // The row leaves the Pending listing, which is the queue doing its job - and the whole of the
    // `?q=` round trip: the box built `/status Pending /and /requested_by ...`, the frontend sent
    // that string, and `SearchQueryParser` on the other side read it the same way.
    await expect(requestRow(admin.page, playerId)).toHaveCount(0, { timeout: 15_000 })

    // **The screen's default really is Pending**: opened with no query of its own it does not show
    // the request that was just resolved, and asking for it by name brings it back with its outcome.
    await admin.page.goto('/admin/requests')
    await expect(admin.page.getByRole('heading', { name: 'Pedidos' })).toBeVisible()
    await expect(requestRow(admin.page, playerId)).toHaveCount(0)
    await admin.page.goto(`/admin/requests?q=${encodeURIComponent(`/requested_by ${playerId}`)}`)
    await expect(requestRow(admin.page, playerId)).toContainText('Aprobado')

    // ---- 5. **The role was granted by UserRoleService and not by a second route.**
    //
    // `/admin/users` reads `users_roles`, so the chip proves the role is live. The Historial reads
    // `user_role_changes`, and *that* table is only ever written by `UserRoleService.grantRole` - so
    // a row there carrying the admin's resolution note as its justification is the proof that
    // approving went through F3.1's service rather than writing the grant itself.
    await admin.page.goto('/admin/users')
    await search(admin.page, 'Buscar usuarios', `/discord_name ${playerId}`)
    const userRow = admin.page.getByRole('row').filter({ hasText: playerId })
    await expect(userRow).toContainText('Master', { timeout: 15_000 })

    await userRow.getByRole('button', { name: 'Historial' }).click()
    const history = admin.page.getByRole('dialog')
    await expect(history).toContainText(resolutionNote)
    await expect(history).toContainText(adminId)
    await history.getByRole('button', { name: 'Close' }).click()

    // ---- 6. The requester is told, once, and the notice opens something.
    //
    // Submitting notifies nobody (#100) and the resolution notifies only the person who asked. The
    // guard in `notificationTarget` was rewritten so these two would be clickable at all; this is
    // the first time one is rendered from a real row.
    await player.page.goto('/player/profile')
    await player.page.getByRole('button', { name: 'Notificaciones' }).click()
    const approved = player.page.getByRole('menuitem').filter({ hasText: 'Aprobaron tu pedido' })
    await expect(approved).toBeVisible()
    await approved.click()
    // Their own profile, and **never** `/admin/requests`: the requester is not an admin.
    await expect(player.page).toHaveURL(/\/player\/profile/)

    // And the profile now shows no request button at all, because the role is held: there is nothing
    // left to ask for.
    await expect(player.page.getByRole('button', { name: 'Pedir el rol de master' })).toHaveCount(0)
  } finally {
    await admin.context.close()
    await player.context.close()
  }
})

/**
 * The other half of the mechanism: being told no, with a reason. A rejection has no effect of its
 * own - which is what makes it a rejection - so what is being checked is that the reason arrives and
 * that the notice opens something.
 */
test('a rejected request notifies the person who asked, and the notice opens', async ({ browser }) => {
  const adminId = `e2e-f32r-admin-${runId}`
  const playerId = `e2e-f32r-player-${runId}`
  const justification = `Una consulta para el equipo (E2E ${runId})`
  const resolutionNote = `No corresponde por ahora (E2E ${runId})`

  const admin = await newActor(browser, adminId, { asAdmin: true })
  const player = await newActor(browser, playerId)

  try {
    // The general request lives on the support screen, for whoever read the help and did not find
    // their answer (fase-3-admin-owner.md:126).
    await player.page.goto('/help')
    await ask(player.page, 'Escribirle a un admin', justification)
    await expect(player.page.getByText('Ya le escribiste a un admin y todavía no te respondieron.')).toBeVisible()

    await admin.page.goto(`/admin/requests?q=${encodeURIComponent(`/status Pending /and /requested_by ${playerId}`)}`)
    const row = requestRow(admin.page, playerId)
    await expect(row).toContainText('Consulta')

    await row.getByRole('button', { name: 'Rechazar' }).click()
    const rejectDialog = admin.page.getByRole('dialog')
    // The screen says what the reason is for before it is written: it is all the other person gets.
    await expect(rejectDialog).toContainText('Es lo único que va a recibir quien pidió')
    await rejectDialog.getByLabel('Tu respuesta').fill(resolutionNote)
    await rejectDialog.getByRole('button', { name: 'Rechazar' }).click()
    await expect(rejectDialog).toBeHidden()
    await expect(requestRow(admin.page, playerId)).toHaveCount(0, { timeout: 15_000 })

    // The requester is told, and the notice opens their profile rather than a screen they cannot enter.
    await player.page.goto('/player/profile')
    await player.page.getByRole('button', { name: 'Notificaciones' }).click()
    const rejected = player.page.getByRole('menuitem').filter({ hasText: 'Rechazaron tu pedido' })
    await expect(rejected).toBeVisible()
    await rejected.click()
    await expect(player.page).toHaveURL(/\/player\/profile/)

    // Rejecting frees the way to ask again - the refusal was about this request, not about the person.
    await player.page.goto('/help')
    await expect(player.page.getByRole('button', { name: 'Escribirle a un admin' })).toBeVisible()
  } finally {
    await admin.context.close()
    await player.context.close()
  }
})

/**
 * **Approving a `TableOpen` creates no table**, which is the rule of this slice most easily
 * implemented one step too far.
 *
 * It is not a refused option but an impossible one: the request carries no name, no system, no seats
 * and no agenda. An admin creates the table `Unassigned` and assigns a master (#72), which is the
 * same circuit from its other end (#90) - and the screen says so instead of doing it.
 */
test('approving a request for a new table records the request and creates no table', async ({ browser }) => {
  const adminId = `e2e-f32t-admin-${runId}`
  const playerId = `e2e-f32t-player-${runId}`
  const justification = `No hay mesas de terror los jueves (E2E ${runId})`

  const admin = await newActor(browser, adminId, { asAdmin: true })
  const player = await newActor(browser, playerId)

  try {
    // The explorer, which is where somebody looks for a table and does not find one - **and the
    // button is only offered from its two empty states**, because a request for a new table is what
    // there is to do when the search came back with nothing. So the search has to come back with
    // nothing, which is what the run marker in the `?q=` is for.
    await player.page.goto(`/player?q=${encodeURIComponent(`nada-coincide-${runId}`)}`)
    await expect(player.page.getByRole('button', { name: 'Pedir que se abra una mesa' })).toBeVisible()
    await ask(player.page, 'Pedir que se abra una mesa', justification)
    await expect(player.page.getByText('Ya pediste que se abra una mesa y todavía no te respondieron.')).toBeVisible()

    // Which tables are waiting for a master before the approval, so the assertion afterwards is
    // about the approval and not about the fixture.
    const unassignedBefore = await unassignedTableIds(admin)

    await admin.page.goto(`/admin/requests?q=${encodeURIComponent(`/status Pending /and /requested_by ${playerId}`)}`)
    const row = requestRow(admin.page, playerId)
    await expect(row).toContainText('Mesa nueva')

    await row.getByRole('button', { name: 'Aprobar' }).click()
    const approveDialog = admin.page.getByRole('dialog')
    // The screen says the table is not created automatically, before the press and not after it.
    await expect(approveDialog).toContainText('La mesa no se crea sola')
    await approveDialog.getByLabel('Tu respuesta').fill(`Buscamos master (E2E ${runId})`)
    await approveDialog.getByRole('button', { name: 'Aprobar' }).click()
    await expect(approveDialog).toBeHidden()
    await expect(requestRow(admin.page, playerId)).toHaveCount(0, { timeout: 15_000 })

    const appeared = [...(await unassignedTableIds(admin))].filter((id) => !unassignedBefore.has(id))
    expect(appeared).toEqual([])

    // The request is resolved, which is the whole of what approving it did.
    await admin.page.goto(`/admin/requests?q=${encodeURIComponent(`/requested_by ${playerId}`)}`)
    await expect(requestRow(admin.page, playerId)).toContainText('Aprobado')
  } finally {
    await admin.context.close()
    await player.context.close()
  }
})
