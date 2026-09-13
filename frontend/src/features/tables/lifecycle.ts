import type { GameTableStatus } from './types'

/**
 * The statuses in which a table is still its master's to rewrite (#245).
 *
 * **Mirror of `GameTableService.EDITABLE_STATUSES`**, and written once here rather than in each
 * screen that asks: it used to be a literal repeated in three of them — the edit page, the detail
 * that shows the button, and the schedule tab — which is three chances to update two.
 *
 * `Preparation` is deliberately absent. A table sent to review is being read by somebody else, and
 * moving it while they read is how a reviewer approves something that no longer exists. The two ways
 * back in are the two that mean "it is yours again": not sent yet, or returned with changes asked.
 */
export const MASTER_EDITABLE_STATUSES: readonly GameTableStatus[] = ['Draft', 'ChangesRequested']

/** Whether this table's master may still rewrite it. */
export function isMasterEditable(status: GameTableStatus): boolean {
  return MASTER_EDITABLE_STATUSES.includes(status)
}
