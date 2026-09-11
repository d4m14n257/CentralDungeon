import { test, expect, type APIRequestContext, type Browser } from '@playwright/test'

import { addScheduleSlot, chooseRequiredCatalogs } from './helpers/tableWizard'

/**
 * `/my/files` end to end, against the real backend.
 *
 * Three things only a real round trip proves, and each one is a claim a mock would happily fake:
 *
 * - **Deduplication is visible** (#234, #75). Uploading the same bytes twice answers 200 with the
 *   row that already existed, and the screen says so. A mock can assert the call; only the real
 *   backend decides the status.
 * - **The uses are real** (#232). The chip that says "Mesa Biblioteca" appears because the file is
 *   attached to that table, resolved by two JPQL queries against MySQL — not because a screen
 *   remembered where it came from.
 * - **Letting go of a file marks it and does not erase it** (#25, #66).
 *
 * Login through TestLoginController (the backend's `test` profile), like every other spec.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

async function testLogin(request: APIRequestContext, discordId: string, asMaster = false) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asMaster, asAdmin: false },
  })
  expect(response.ok()).toBeTruthy()
}

async function newAuthenticatedPage(browser: Browser, discordId: string, asMaster: boolean) {
  const context = await browser.newContext()
  await testLogin(context.request, discordId, asMaster)
  const page = await context.newPage()
  return { context, page }
}

/** A PDF small enough for the cap and real enough for the MIME whitelist to accept it. */
function pdf(name: string, content: string) {
  return { name, mimeType: 'application/pdf', buffer: Buffer.from(`%PDF-1.4 ${content}`) }
}

test('the library shows what each file is, where it is used, and reuses an upload it recognises', async ({ browser }) => {
  const tableName = `Mesa Biblioteca E2E ${runId}`
  const master = await newAuthenticatedPage(browser, `e2e-myfiles-${runId}`, true)
  const { page } = master

  try {
    // A table for the file to be used on, so the usage chip has something to say.
    await page.goto('/master/tables/new')
    await page.getByRole('textbox', { name: 'Nombre' }).fill(tableName)
    await page.getByRole('button', { name: 'Siguiente' }).click()
    await chooseRequiredCatalogs(page)
    await page.getByRole('button', { name: 'Siguiente' }).click()
    await addScheduleSlot(page, '20:00', 'Viernes')
    await page.getByRole('button', { name: 'Siguiente' }).click()
    await page.getByRole('button', { name: 'Siguiente' }).click()
    await page.getByRole('button', { name: 'Crear mesa' }).click()
    await expect(page.getByRole('heading', { name: tableName })).toBeVisible()
    const tableId = page.url().split('/master/tables/')[1] as string

    // Uploaded from the library, which is the one place with no flow to observe — so it is also the
    // one place that asks which cajón (#233).
    await page.goto('/my/files')
    await page.getByRole('button', { name: 'Subir archivo' }).click()
    await page.getByRole('combobox').first().click()
    await page.getByRole('option', { name: 'Solicitud de jugador' }).click()
    await page.locator('input[type="file"]').setInputFiles(pdf(`ficha-${runId}.pdf`, runId))

    const row = page.getByRole('listitem').filter({ hasText: `ficha-${runId}.pdf` })
    await expect(row).toBeVisible()
    await expect(row.getByText('Solicitud de jugador')).toBeVisible()
    // Nothing uses it yet, and saying so is what warns before the purge does (#75, #232).
    await expect(row.getByText('Sin usar')).toBeVisible()

    // Attach it to the table, reusing it from the history — never uploading it again (#65).
    await page.goto(`/master/tables/${tableId}/files`)
    await page.getByRole('button', { name: 'Agregar un archivo' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByRole('tab', { name: 'Mis archivos' }).click()
    await dialog
      .getByRole('listitem')
      .filter({ hasText: `ficha-${runId}.pdf` })
      .getByRole('button', { name: 'Usar' })
      .click()
    await expect(dialog).toBeHidden()

    // The use now shows, resolved from the link and not remembered by the screen. And the file is in
    // **two** cajones: the one it was uploaded into and the one attaching it added (#233). That is
    // the case a single column could never express.
    await page.goto('/my/files')
    await expect(row.getByText(tableName)).toBeVisible()
    await expect(row.getByText('Sin usar')).toBeHidden()
    await expect(row.getByText('Solicitud de jugador')).toBeVisible()
    await expect(row.getByText('De mesa')).toBeVisible()

    // The same bytes again: recognised, not stored twice, and the screen says so (#234).
    await page.getByRole('button', { name: 'Subir archivo' }).click()
    await page.locator('input[type="file"]').setInputFiles(pdf(`otra-copia-${runId}.pdf`, runId))
    await expect(page.getByText(/ya lo tenías subido/)).toBeVisible()
    // The name that survives is the first one: the row was recognised, not rewritten.
    await expect(page.getByRole('listitem').filter({ hasText: `otra-copia-${runId}.pdf` })).toHaveCount(0)
  } finally {
    await master.context.close()
  }
})

/**
 * The cajones offered are the reader's own (#237).
 *
 * `/my/files` is the **personal** library: a plain member of the community never files table
 * material, and an announcement is nobody's — it is the community's, always published, and it lives
 * only in `/admin/files`. Whoever runs tables gets the master-side ones as well.
 */
test('the library offers only the cajones that are the reader’s own', async ({ browser }) => {
  const player = await newAuthenticatedPage(browser, `e2e-myfiles-player-${runId}`, false)
  const master = await newAuthenticatedPage(browser, `e2e-myfiles-master-${runId}`, true)

  try {
    await player.page.goto('/my/files')
    const playerFilter = player.page.getByRole('group', { name: 'Filtrar por dónde se usa' })
    await expect(playerFilter.getByRole('button', { name: 'Solicitud de jugador' })).toBeVisible()
    await expect(playerFilter.getByRole('button', { name: 'Entrega del jugador' })).toBeVisible()
    // Not theirs: filing something of their own as table material is not a thing a player does.
    await expect(playerFilter.getByRole('button', { name: 'De mesa' })).toHaveCount(0)
    await expect(playerFilter.getByRole('button', { name: 'Petición del master' })).toHaveCount(0)
    // Nobody's, whoever is asking.
    await expect(playerFilter.getByRole('button', { name: 'Anuncios de la comunidad' })).toHaveCount(0)

    await master.page.goto('/my/files')
    const masterFilter = master.page.getByRole('group', { name: 'Filtrar por dónde se usa' })
    await expect(masterFilter.getByRole('button', { name: 'De mesa' })).toBeVisible()
    // A master is also a person who plays (#38), so they keep the player-side ones too.
    await expect(masterFilter.getByRole('button', { name: 'Solicitud de jugador' })).toBeVisible()
    await expect(masterFilter.getByRole('button', { name: 'Anuncios de la comunidad' })).toHaveCount(0)
  } finally {
    await player.context.close()
    await master.context.close()
  }
})

test('a file can be renamed and let go of', async ({ browser }) => {
  const master = await newAuthenticatedPage(browser, `e2e-myfiles-edit-${runId}`, true)
  const { page } = master

  try {
    await page.goto('/my/files')
    await page.getByRole('button', { name: 'Subir archivo' }).click()
    await page.locator('input[type="file"]').setInputFiles(pdf(`mapa-${runId}.pdf`, `mapa ${runId}`))
    await expect(page.getByRole('listitem').filter({ hasText: `mapa-${runId}.pdf` })).toBeVisible()

    // Renaming touches metadata only — the content lives under a generated key (#80). There is no
    // cajón to correct here: a membership is what the file's uses made true and is never revoked.
    await page
      .getByRole('listitem')
      .filter({ hasText: `mapa-${runId}.pdf` })
      .getByRole('button', { name: 'Editar' })
      .click()
    const dialog = page.getByRole('dialog')
    await dialog.getByRole('textbox', { name: 'Nombre' }).fill(`El pantano ${runId}.pdf`)
    await expect(dialog.getByRole('combobox')).toHaveCount(0)
    await dialog.getByRole('button', { name: 'Guardar' }).click()

    const renamed = page.getByRole('listitem').filter({ hasText: `El pantano ${runId}.pdf` })
    await expect(renamed).toBeVisible()

    // Letting go marks the row; the bytes wait for the platform owner, which is F5 (#25, #66).
    await renamed.getByRole('button', { name: 'Dar de baja' }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Dar de baja' }).click()
    await expect(page.getByRole('listitem').filter({ hasText: `El pantano ${runId}.pdf` })).toHaveCount(0)
  } finally {
    await master.context.close()
  }
})
