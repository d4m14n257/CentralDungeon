import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

/**
 * F1.4 end to end, against the real backend: the criterion of `fase-1-master.md` §4 — *a master
 * uploads a character sheet, attaches it to two tables without duplicating it, and the player
 * downloads it from the public detail*.
 *
 * What no unit test proves and this does: that **one upload attached to two tables is still one
 * file** (#79) — which a mock can only assert about a call, not about a row — that a private
 * attachment is genuinely **absent** from what a player receives rather than hidden by the screen,
 * and that publishing from /admin/files lets a master use the community's sheet without copying it.
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

/**
 * The smallest table the wizard will make: F1.4 needs a table to attach things to, not a calendar.
 *
 * @param page the tab, already authenticated as a master
 * @param name the table's name
 * @returns the id of the created table
 */
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

/** A PDF small enough to be under the cap and real enough for the MIME whitelist to accept it. */
function pdf(name: string, content: string) {
  return { name, mimeType: 'application/pdf', buffer: Buffer.from(`%PDF-1.4 ${content}`) }
}

/**
 * Attaches a file to a table from the Archivos tab, either by uploading it or by reusing one.
 *
 * @param page      the tab, already on the table's files tab
 * @param isPrivate whether the attachment stays with whoever runs the table
 * @param pick      how to choose the file once the dialog is open
 */
async function attach(page: Page, isPrivate: boolean, pick: (dialog: ReturnType<Page['getByRole']>) => Promise<void>) {
  await page.getByRole('button', { name: 'Agregar un archivo' }).click()
  const dialog = page.getByRole('dialog')
  if (isPrivate) {
    await dialog.getByRole('checkbox', { name: 'Solo para quienes dirigen la mesa' }).click()
  }
  await pick(dialog)
  await expect(dialog).toBeHidden()
}

/** The headline case of #79: one upload, two tables, one file — and the player can read it. */
test('a file attached to two tables is stored once, and the player downloads it', async ({ browser }) => {
  const firstName = `Mesa Archivos A E2E ${runId}`
  const secondName = `Mesa Archivos B E2E ${runId}`
  const playerDiscordId = `e2e-file-player-${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-file-master-${runId}`, true, false)
  const admin = await newAuthenticatedPage(browser, `e2e-file-admin-${runId}`, false, true)
  const player = await newAuthenticatedPage(browser, playerDiscordId, false, false)

  try {
    const firstId = await createTable(master.page, firstName)
    const secondId = await createTable(master.page, secondName)

    // Upload it on the first table.
    await master.page.goto(`/master/tables/${firstId}/files`)
    await attach(master.page, false, async (dialog) => {
      await dialog.getByLabel('Elegir un archivo para subir').setInputFiles(pdf('ficha-e2e.pdf', runId))
    })
    await expect(master.page.getByText('ficha-e2e.pdf')).toBeVisible()

    // Reuse it on the second: the same file, chosen from the history, never uploaded again (#65).
    await master.page.goto(`/master/tables/${secondId}/files`)
    await attach(master.page, false, async (dialog) => {
      await dialog.getByRole('tab', { name: 'Mis archivos' }).click()
      await dialog.getByRole('listitem').filter({ hasText: 'ficha-e2e.pdf' }).getByRole('button', { name: 'Usar' }).click()
    })
    await expect(master.page.getByText('ficha-e2e.pdf')).toBeVisible()

    // One row, two tables. This is the assertion the whole slice exists for (#79).
    await admin.page.goto('/admin/files')
    await admin.page.getByRole('combobox', { name: 'Buscar archivos' }).fill('ficha-e2e')
    const row = admin.page.getByRole('row', { name: /ficha-e2e\.pdf/ })
    await expect(row).toHaveCount(1)
    await expect(row).toContainText('2 mesas')

    // The player reads it from the public detail, without belonging to the table.
    await player.page.goto(`/tables/${firstId}`)
    await expect(player.page.getByText('ficha-e2e.pdf')).toBeVisible()
    const download = player.page.waitForEvent('download')
    await player.page.getByRole('button', { name: 'Descargar' }).first().click()
    expect((await download).suggestedFilename()).toBe('ficha-e2e.pdf')
  } finally {
    await player.context.close()
    await admin.context.close()
    await master.context.close()
  }
})

/**
 * Detaching is not deleting (#79): the file survives on the other table and in the master's own
 * history, which is what makes the community's shared sheet safe to attach anywhere.
 */
test('taking a file off one table leaves it on the other', async ({ browser }) => {
  const firstName = `Mesa Quitar A E2E ${runId}`
  const secondName = `Mesa Quitar B E2E ${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-detach-master-${runId}`, true, false)

  try {
    const firstId = await createTable(master.page, firstName)
    const secondId = await createTable(master.page, secondName)

    await master.page.goto(`/master/tables/${firstId}/files`)
    await attach(master.page, false, async (dialog) => {
      await dialog.getByLabel('Elegir un archivo para subir').setInputFiles(pdf('mapa-e2e.pdf', runId))
    })

    await master.page.goto(`/master/tables/${secondId}/files`)
    await attach(master.page, false, async (dialog) => {
      await dialog.getByRole('tab', { name: 'Mis archivos' }).click()
      await dialog.getByRole('listitem').filter({ hasText: 'mapa-e2e.pdf' }).getByRole('button', { name: 'Usar' }).click()
    })
    await expect(master.page.getByText('mapa-e2e.pdf')).toBeVisible()

    await master.page.goto(`/master/tables/${firstId}/files`)
    await master.page.getByRole('button', { name: 'Quitar de la mesa' }).click()
    // The confirmation says the file survives — "quitar" reads as "borrar" unless something says so.
    await expect(master.page.getByRole('dialog').getByText(/sigue existiendo/)).toBeVisible()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
    await expect(master.page.getByText('mapa-e2e.pdf')).toBeHidden()

    await master.page.goto(`/master/tables/${secondId}/files`)
    await expect(master.page.getByText('mapa-e2e.pdf')).toBeVisible()
  } finally {
    await master.context.close()
  }
})

/**
 * A private attachment is **absent** from what a player receives, not hidden on the way out. The
 * assertion is on the public detail rather than on a CSS state for exactly that reason.
 */
test('a private attachment never reaches the public detail', async ({ browser }) => {
  const tableName = `Mesa Privada E2E ${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-priv-master-${runId}`, true, false)
  const player = await newAuthenticatedPage(browser, `e2e-priv-player-${runId}`, false, false)

  try {
    const tableId = await createTable(master.page, tableName)

    await master.page.goto(`/master/tables/${tableId}/files`)
    await attach(master.page, true, async (dialog) => {
      await dialog.getByLabel('Elegir un archivo para subir').setInputFiles(pdf('notas-e2e.pdf', runId))
    })
    await expect(master.page.getByText('Solo masters')).toBeVisible()

    await player.page.goto(`/tables/${tableId}`)
    await expect(player.page.getByRole('heading', { name: tableName })).toBeVisible()
    await expect(player.page.getByText('notas-e2e.pdf')).toBeHidden()
  } finally {
    await player.context.close()
    await master.context.close()
  }
})

/**
 * #79 from the platform's side: an admin publishes a file and a master who never uploaded it attaches
 * it — linked, not copied, which is why it still shows a single owner in /admin/files.
 */
test('a master attaches a file the platform published without copying it', async ({ browser }) => {
  const tableName = `Mesa Publicada E2E ${runId}`
  const adminDiscordId = `e2e-pub-admin-${runId}`
  const admin = await newAuthenticatedPage(browser, adminDiscordId, true, true)
  const master = await newAuthenticatedPage(browser, `e2e-pub-master-${runId}`, true, false)

  try {
    // The admin needs a table only to get at an upload box; publishing is done from /admin/files.
    const adminTableId = await createTable(admin.page, `Mesa Admin Publicar E2E ${runId}`)
    await admin.page.goto(`/master/tables/${adminTableId}/files`)
    await attach(admin.page, false, async (dialog) => {
      await dialog.getByLabel('Elegir un archivo para subir').setInputFiles(pdf('ficha-comunidad-e2e.pdf', runId))
    })

    await admin.page.goto('/admin/files')
    await admin.page.getByRole('combobox', { name: 'Buscar archivos' }).fill('ficha-comunidad-e2e')
    const row = admin.page.getByRole('row', { name: /ficha-comunidad-e2e\.pdf/ })
    await row.getByRole('button', { name: 'Publicar' }).click()
    // The audience is a required choice with no default that could publish to the wrong people (M24.1).
    await admin.page.getByRole('dialog').getByRole('button', { name: 'Publicar' }).click()
    await expect(row).toContainText('Publicado')

    const tableId = await createTable(master.page, tableName)
    await master.page.goto(`/master/tables/${tableId}/files`)
    await attach(master.page, false, async (dialog) => {
      await dialog.getByRole('tab', { name: 'Publicados' }).click()
      await dialog.getByRole('listitem').filter({ hasText: 'ficha-comunidad-e2e.pdf' }).getByRole('button', { name: 'Usar' }).click()
    })
    await expect(master.page.getByText('ficha-comunidad-e2e.pdf')).toBeVisible()

    // Still one file, still the admin's: attaching linked it, it did not copy it (#79).
    await admin.page.goto('/admin/files')
    await admin.page.getByRole('combobox', { name: 'Buscar archivos' }).fill('ficha-comunidad-e2e')
    await expect(admin.page.getByRole('row', { name: /ficha-comunidad-e2e\.pdf/ })).toHaveCount(1)
  } finally {
    await master.context.close()
    await admin.context.close()
  }
})

/**
 * The refusals of #197: what the person reads names the actual problem, in their language, instead of
 * "no se pudo guardar". The legacy had no limit of any kind, so there was nothing to explain (M21.3).
 */
test('an unaccepted file type is refused with a message that says why', async ({ browser }) => {
  const tableName = `Mesa Limite E2E ${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-limit-master-${runId}`, true, false)

  try {
    const tableId = await createTable(master.page, tableName)
    await master.page.goto(`/master/tables/${tableId}/files`)

    await master.page.getByRole('button', { name: 'Agregar un archivo' }).click()
    const dialog = master.page.getByRole('dialog')
    // The cap is stated before anything is tried, not only after a refusal.
    await expect(dialog.getByText(/2 MB/)).toBeVisible()

    await dialog.getByLabel('Elegir un archivo para subir').setInputFiles({
      name: 'trampa-e2e.exe',
      mimeType: 'application/x-msdownload',
      buffer: Buffer.from('MZ'),
    })

    await expect(master.page.getByText(/no se acepta|no está permitido/i)).toBeVisible()
  } finally {
    await master.context.close()
  }
})
