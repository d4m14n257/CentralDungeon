import { expect, type Page } from '@playwright/test'

/** A file to attach on the way through, as Playwright's `setInputFiles` wants it. */
interface AttachedFile {
  name: string
  mimeType: string
  buffer: Buffer
}

/**
 * Applies to a table from its public detail, walking the dialog's two steps.
 *
 * **The second step arrived with F2.2 and broke four specs at once** (#238, #247). Applying used to
 * be one form: open the dialog, press «Postularme», done. Now the dialog is *write and attach* →
 * *review and send*, because an application cannot be edited once it is sent and the character sheet
 * that goes with it is attached forever. Every spec that only cared about *being* a player — to test
 * sessions, tasks or a schedule clash — was pressing the same button twice and timing out on a dialog
 * that had moved on.
 *
 * It lives here for the same reason {@code submitForReview} does: four copies of the same three lines
 * break together, and the next step added to this flow should be one edit.
 *
 * @param page    the applicant's page, already on `/player/tables/:id`
 * @param options the message to write and the files to attach; both optional, because an application
 *                with neither is valid and is what most specs want
 */
export async function applyToTable(page: Page, options: { message?: string; files?: AttachedFile[] } = {}): Promise<void> {
  await page.getByRole('button', { name: 'Postularme' }).click()
  const dialog = page.getByRole('dialog')

  if (options.message !== undefined) {
    await dialog.getByLabel('Mensaje para el master').fill(options.message)
  }
  if (options.files !== undefined && options.files.length > 0) {
    await dialog.locator('input[type="file"]').setInputFiles(options.files)
    for (const file of options.files) {
      await expect(dialog.getByText(file.name)).toBeVisible()
    }
  }

  await dialog.getByRole('button', { name: 'Siguiente' }).click()
  await dialog.getByRole('button', { name: 'Postularme' }).click()
  await expect(dialog).toBeHidden()
}
