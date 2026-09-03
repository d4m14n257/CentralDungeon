import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

/**
 * E2 sub-rebanada 1, de punta a punta (decisiones.md #163): un master crea una mesa por el
 * wizard -> Preparation; un admin la aprueba desde /admin/tables -> Opened; el Primary la inicia
 * -> InProgress; el Primary la finaliza -> Finished. Cubre la máquina de estados real, no el
 * self-service que E1 tenía.
 *
 * Login por TestLoginController (backend "test" profile), igual que registration-flow.spec.ts -
 * no hay Discord real todavía.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

async function testLogin(request: APIRequestContext, discordId: string, asMaster = false, asAdmin = false) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, {
    params: { discordId, asMaster, asAdmin },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as { accessToken: string }
}

async function newAuthenticatedPage(browser: Browser, discordId: string, asMaster: boolean, asAdmin: boolean) {
  const context = await browser.newContext()
  await testLogin(context.request, discordId, asMaster, asAdmin)
  const page = await context.newPage()
  return { context, page }
}

/**
 * Crea una mesa por el wizard de cuatro pasos de F1.2. Estos tests son sobre la máquina de estados,
 * no sobre la agenda, así que pasan de largo por los tres pasos que no piden nada obligatorio.
 *
 * @param page  la pestaña, ya autenticada como master
 * @param name  el nombre de la mesa
 */
async function createTableThroughWizard(page: Page, name: string) {
  await page.goto('/master/tables/new')
  await page.getByRole('textbox', { name: 'Nombre' }).fill(name)
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await page.getByRole('button', { name: 'Crear mesa' }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()
}

/**
 * Asignar masters con el buscador de personas (decisiones.md #164, #165): un admin crea una mesa
 * sin master, busca a dos personas —una por texto suelto, la otra acotando con /discord_name—,
 * asciende a la segunda y asigna. La mesa nace Unassigned y sale de la bandeja al quedar Opened.
 */
test('an admin searches for people and assigns the masters of an unassigned table', async ({ browser }) => {
  const adminDiscordId = `e2e-assign-admin-${runId}`
  const firstCandidate = `e2e-cand-a-${runId}`
  const secondCandidate = `e2e-cand-b-${runId}`
  const tableName = `Mesa Sin Master E2E ${runId}`

  // Las dos personas tienen que existir para poder encontrarlas: el login de prueba las crea.
  const candidates = await browser.newContext()
  await testLogin(candidates.request, firstCandidate)
  await testLogin(candidates.request, secondCandidate)
  await candidates.close()

  const admin = await newAuthenticatedPage(browser, adminDiscordId, false, true)
  try {
    await admin.page.goto('/admin/tables')
    await admin.page.getByRole('button', { name: 'Crear mesa sin master' }).click()
    const createDialog = admin.page.getByRole('dialog')
    await createDialog.getByRole('textbox', { name: 'Nombre' }).fill(tableName)
    await createDialog.getByRole('button', { name: 'Crear mesa sin master' }).click()

    const row = admin.page.getByRole('listitem').filter({ hasText: tableName })
    await expect(row).toBeVisible()
    await row.getByRole('button', { name: 'Asignar masters' }).click()

    const dialog = admin.page.getByRole('dialog')
    const search = dialog.getByRole('combobox', { name: 'Buscar personas' })
    // Anclado al principio: el nombre suelto también aparece en la X del chip y en la del master.
    const result = (discordId: string) => dialog.getByRole('button', { name: new RegExp(`^${discordId}\\b`) })

    // Con prefijo: Enter cierra el criterio en un chip, que acota la búsqueda a un solo campo.
    // El input siempre se llena estando vacío -Enter y la X del chip lo vacían solos-. Escribir
    // encima de texto existente quedó fuera del caso a propósito: en una corrida previa de esta
    // suite el `fill` sobre el input ya cargado terminó con el input en blanco, y el manejo de
    // teclas ya está cubierto por SearchQueryInput.test.tsx, que no depende del navegador.
    await search.fill(`/discord_name ${firstCandidate}`)
    await search.press('Enter')
    await expect(dialog.getByText('Discord:')).toBeVisible()
    await result(firstCandidate).click()

    // Quitar el chip devuelve la búsqueda al criterio básico: nombre de Discord o del sistema.
    await dialog.getByRole('button', { name: `Quitar criterio: ${firstCandidate}` }).click()
    await search.fill(secondCandidate)
    await result(secondCandidate).click()

    // El primero entró de Primary; ascender al segundo degrada al primero.
    await dialog.getByRole('button', { name: `Hacer master a ${secondCandidate}` }).click()
    await expect(dialog.getByRole('button', { name: `Hacer master a ${firstCandidate}` })).toBeVisible()

    await dialog.getByRole('button', { name: 'Asignar masters' }).click()
    await expect(row).toBeHidden()
  } finally {
    await admin.context.close()
  }
})

/**
 * Borrar lo que nunca fue público (decisiones.md #175): el master arma una mesa, decide que no va
 * a pasar y la elimina antes de publicarla. Una vez abierta ya no se puede: eso se cancela.
 */
test('a master deletes a draft that never went public, and cannot delete it once open', async ({ browser }) => {
  const masterDiscordId = `e2e-del-master-${runId}`
  const adminDiscordId = `e2e-del-admin-${runId}`
  const draftName = `Mesa Borrador E2E ${runId}`
  const openedName = `Mesa Publicada E2E ${runId}`

  const master = await newAuthenticatedPage(browser, masterDiscordId, true, false)
  try {
    // Un borrador: se crea y se elimina sin que nadie lo haya visto.
    await createTableThroughWizard(master.page, draftName)

    await master.page.getByRole('link', { name: 'Estado' }).click()
    await master.page.getByRole('button', { name: 'Eliminar mesa' }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()

    // Vuelve al listado y la mesa ya no está en ningún lado.
    await expect(master.page).toHaveURL(/\/master\/tables$/)
    await expect(master.page.getByText(draftName)).toBeHidden()

    // Una mesa aprobada ya fue pública: el botón de eliminar no existe.
    // La URL recién sirve cuando la navegación terminó, y el helper ya espera al encabezado.
    await createTableThroughWizard(master.page, openedName)
    const tableId = master.page.url().split('/master/tables/')[1]

    const admin = await newAuthenticatedPage(browser, adminDiscordId, false, true)
    try {
      await admin.page.goto('/admin/tables')
      const row = admin.page.getByRole('listitem').filter({ hasText: openedName })
      await row.getByRole('button', { name: 'Aprobar' }).click()
      await admin.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
      await expect(row).toBeHidden()
    } finally {
      await admin.context.close()
    }

    await master.page.goto(`/master/tables/${tableId}/status`)
    await expect(master.page.getByText('Abierta', { exact: true })).toBeVisible()
    await expect(master.page.getByRole('button', { name: 'Eliminar mesa' })).toBeHidden()
    await expect(master.page.getByRole('button', { name: 'Cancelar mesa' })).toBeVisible()
  } finally {
    await master.context.close()
  }
})

test('a master creates a table, an admin approves it, and the master runs it end to end', async ({ browser }) => {
  const masterDiscordId = `e2e-master-${runId}`
  const adminDiscordId = `e2e-admin-${runId}`
  const tableName = `Mesa Ciclo E2E ${runId}`

  const master = await newAuthenticatedPage(browser, masterDiscordId, true, false)
  try {
    await createTableThroughWizard(master.page, tableName)
    await expect(master.page.getByText('En preparación', { exact: true })).toBeVisible()
    const tableUrl = master.page.url()
    const tableId = tableUrl.split('/master/tables/')[1]

    const admin = await newAuthenticatedPage(browser, adminDiscordId, false, true)
    try {
      await admin.page.goto('/admin/tables')
      const row = admin.page.getByRole('listitem').filter({ hasText: tableName })
      await expect(row).toBeVisible()
      await row.getByRole('button', { name: 'Aprobar' }).click()
      await admin.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
      // /admin/tables solo lista lo que espera revisión (Unassigned/Preparation/ChangesRequested):
      // una vez Opened, la fila desaparece de esta bandeja en vez de quedar con el badge actualizado.
      await expect(row).toBeHidden()
    } finally {
      await admin.context.close()
    }

    await master.page.goto(`/master/tables/${tableId}/status`)
    await expect(master.page.getByRole('heading', { name: tableName })).toBeVisible()
    await expect(master.page.getByText('Abierta', { exact: true })).toBeVisible()

    await master.page.getByRole('button', { name: 'Iniciar mesa' }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
    await expect(master.page.getByText('En curso', { exact: true })).toBeVisible()

    await master.page.getByRole('button', { name: 'Finalizar mesa' }).click()
    await master.page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
    await expect(master.page.getByText('Finalizada', { exact: true })).toBeVisible()
  } finally {
    await master.context.close()
  }
})
