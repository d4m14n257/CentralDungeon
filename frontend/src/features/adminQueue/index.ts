/**
 * Public surface of the admin tray feature (#114, #100): the one screen that says what is waiting on
 * an admin, whichever table the work lives in, and the reservation that keeps two of them from
 * resolving the same thing. Anything not listed here is private to it.
 *
 * **The feature owns the tray and nothing it resolves.** Approving a request belongs to `approvals`
 * and approving a table belongs to `tables`, because those are the aggregates the acts are about — a
 * feature never imports another (§3.1.5), so the screen is what composes the three.
 */

export { ClaimBadge } from './components/ClaimBadge'
export { QueueItemKindBadge } from './components/QueueItemKindBadge'
export { useAdminQueue } from './api/useAdminQueue'
export { useClaimItem } from './api/useClaimItem'
export { useReleaseItem } from './api/useReleaseItem'
export { ADMIN_QUEUE_ITEM_KINDS, ADMIN_QUEUE_ITEM_TYPES } from './queueKinds'
export { ADMIN_QUEUE_ERROR_CODES, adminQueueErrorKey } from './queueErrors'
/** The feature's domain types. Each is written once in `types.ts` and derived from there (§3.2). */
export { isClaimedByReader } from './types'
export type { AdminQueueItem, AdminQueueItemKind, AdminQueueItemType } from './types'
