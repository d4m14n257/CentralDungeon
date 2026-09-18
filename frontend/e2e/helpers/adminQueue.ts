import { expect, type Page } from '@playwright/test'

/**
 * Reviewing a table, from where F3.3 moved it to (#176, #100).
 *
 * **Seven specs had their own copy of these six lines** pointed at `/admin/tables`, which is exactly
 * why the move of one slice broke seven files at once. One copy here: when the review moves again, or
 * grows a step, it is one edit rather than a hunt.
 *
 * **These helpers do not take the item first, on purpose.** An earlier version did, because the rule
 * as first written required holding a row before resolving it — the same rule that made
 * `/admin/requests`, which cannot reserve anything, refuse every resolution it offered. The corrected
 * rule is that resolving something nobody holds takes it implicitly, and what is refused is acting on
 * what a colleague holds. So the shortest honest path through the screen is the one an admin actually
 * walks: open the tray, press the button. Reserving first is a separate behaviour and belongs in a
 * spec about reserving, not baked into every table that needs approving to reach the next assertion.
 *
 * The row is a `<tr>`: `/admin/queue` is one of the wide tables of `frontend-diseno.md` §5.b, and the
 * card layout it falls back to below `md` is `display:none` at Playwright's viewport, so it is not in
 * the accessibility tree at all.
 */

/** The tray row whose text contains `name`, in the wide table. */
function queueRow(page: Page, name: string) {
  return page.getByRole('row').filter({ hasText: name })
}

/**
 * Takes a row of the tray for the signed-in admin, so nobody else sees it.
 *
 * Not needed to resolve anything — see the note above — so it is used by the specs that are about the
 * reservation itself.
 *
 * @param page the admin's page
 * @param name what identifies the row - a table's name, a requester's handle
 */
export async function claimQueueItem(page: Page, name: string) {
  const row = queueRow(page, name)
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: 'Tomar' }).click()
  // The row is the reader's once it offers handing it back.
  await expect(row.getByRole('button', { name: 'Soltar' })).toBeVisible()
}

/**
 * Approves a table from the shared tray, and waits for it to leave.
 *
 * @param page the admin's page
 * @param name the table's name
 */
export async function approveTableFromQueue(page: Page, name: string) {
  await page.goto('/admin/queue')
  const row = queueRow(page, name)
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: 'Aprobar' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Confirmar' }).click()
  // Resolved work is not work waiting on anybody: the row leaves the tray rather than staying with
  // an updated badge.
  await expect(row).toBeHidden()
}

/**
 * Sends a table back to its master with a reason, from the shared tray.
 *
 * @param page          the admin's page
 * @param name          the table's name
 * @param justification what the master has to fix - required, and what they read on the status tab
 */
export async function requestChangesFromQueue(page: Page, name: string, justification: string) {
  await page.goto('/admin/queue')
  const row = queueRow(page, name)
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: 'Pedir cambios' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('textbox').fill(justification)
  await dialog.getByRole('button', { name: 'Pedir cambios' }).click()
  await expect(dialog).toBeHidden()
}
