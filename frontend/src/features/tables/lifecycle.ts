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

/**
 * Every status a table can be shown in, in the order of its life (#245).
 *
 * A written-out tuple rather than something derived from the union, for the same reason as
 * `PLATFORM_ROLES`: a union of literals has no runtime value to iterate, and `satisfies` is what
 * turns a typo here into a compile error.
 *
 * **`Deleted` is not one of them and never reaches this side.** The backend's enum has it, and a
 * deleted table is gone for everybody (#175) — so the union does not carry it, `/admin/tables`
 * excludes it by default, and `/table_status` cannot offer it. A command that offers a value no row
 * can have is a filter that always answers nothing.
 */
export const ALL_TABLE_STATUSES = [
  'Draft',
  'Unassigned',
  'Preparation',
  'ChangesRequested',
  'Opened',
  'InProgress',
  'PauseRequested',
  'Pause',
  'Finished',
  'Canceled',
] as const satisfies readonly GameTableStatus[]

/** Whether this table's master may still rewrite it. */
export function isMasterEditable(status: GameTableStatus): boolean {
  return MASTER_EDITABLE_STATUSES.includes(status)
}
