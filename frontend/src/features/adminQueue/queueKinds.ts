import type { AdminQueueItemKind, AdminQueueItemType } from './types'

/**
 * The two kinds of work the tray carries in F3.3, in the order they are listed and labelled.
 *
 * A written-out tuple rather than something derived from the union, for the same reason as
 * `PLATFORM_ROLES` and `APPROVAL_REQUEST_TYPES`: a union of literals has no runtime value to
 * iterate, and `satisfies` is what turns a typo here into a compile error.
 *
 * **`CommentUnderReview` and `FeedbackNew` are deliberately absent.** `docs/modelo-datos.md` §5
 * names four sources and F5 brings the other two **with the query that produces them** — a kind the
 * backend cannot emit is a badge that never renders and a `Record` case nobody can test.
 */
export const ADMIN_QUEUE_ITEM_KINDS = ['ApprovalRequest', 'TableWaitingReview'] as const satisfies readonly AdminQueueItemKind[]

/**
 * The two source discriminators, which are also the first segment of the claim URL.
 *
 * They are the vocabulary of `entity_type` (#78), reused rather than re-coined — see
 * {@link AdminQueueItemType}.
 */
export const ADMIN_QUEUE_ITEM_TYPES = ['approval_request', 'game_table'] as const satisfies readonly AdminQueueItemType[]
