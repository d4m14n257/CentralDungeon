/**
 * Public surface of the admin tray feature: the one screen that says what is waiting on
 * an admin, whichever table the work lives in, and the reservation that keeps two of them from
 * resolving the same thing. Anything not listed here is private to it.
 *
 * **The feature owns the tray and nothing it resolves.** Approving a request belongs to `approvals`
 * and approving a table belongs to `tables`, because those are the aggregates the acts are about — a
 * feature never imports another, so the screen is what composes the three.
 */

export { ClaimBadge } from './components/ClaimBadge'
export { QueueItemKindBadge } from './components/QueueItemKindBadge'
export { useQueue } from './api/useQueue'
export { useClaimItem } from './api/useClaimItem'
export { useReleaseItem } from './api/useReleaseItem'
export { QUEUE_ITEM_KINDS, QUEUE_ITEM_TYPES } from './queueKinds'
export { QUEUE_ERROR_CODES, queueErrorKey } from './queueErrors'
/** The feature's domain types. Each is written once in `types.ts` and derived from there. */
export { isClaimedByReader } from './types'
export type { QueueItem, QueueItemKind, QueueItemType } from './types'
