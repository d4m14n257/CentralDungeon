import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

/**
 * Accepts the review that every create, change or delete opens before writing (#283).
 *
 * The review is the dialog whose title asks — «¿…?» — stacked over whatever form sent it; this finds
 * it by that title and presses the button that is not «Cancelar», so a test reads as what a person
 * does: fill the form, send it, confirm.
 *
 * @returns once the confirmation has been pressed
 */
export async function acceptReview(): Promise<void> {
  const title = await screen.findByRole('heading', { name: /^¿/ })
  const review = title.closest('[role="dialog"]')
  if (!(review instanceof HTMLElement)) {
    throw new Error('The review title is not inside a dialog')
  }
  const buttons = within(review)
    .getAllByRole('button')
    .filter(
      (button) =>
        !/^(Cancelar|Close|Volver)$/.test(button.textContent?.trim() ?? '') && button.getAttribute('data-slot') !== 'dialog-close',
    )
  const confirm = buttons.at(-1)
  if (!confirm) {
    throw new Error('The review has no confirm button')
  }
  await userEvent.click(confirm)
}
