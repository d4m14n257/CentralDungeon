import type { Page } from '@playwright/test'

/**
 * Accepts the review that every create, change or delete opens before writing.
 *
 * The review is the dialog whose title asks a question, stacked over whatever sent it; this presses its
 * confirm button, the one that is not the cancel button. A spec then reads as what a person does: fill the
 * form, send it, confirm.
 *
 * @param page the tab with the review open (or about to open)
 */
export async function acceptReview(page: Page): Promise<void> {
  const review = page.getByRole('dialog').filter({ has: page.getByRole('heading', { name: /^¿/ }) })
  await review
    .getByRole('button')
    .filter({ hasNotText: /^(Cancelar|Close|Cerrar)$/ })
    .last()
    .click()
}
