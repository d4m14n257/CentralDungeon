import type { QueueItemKind, QueueItemType } from './types'

/**
 * The two kinds of work the tray carries today, in the order they are listed and labelled.
 *
 * A written-out tuple rather than something derived from the union, for the same reason as
 * `PLATFORM_ROLES` and `APPROVAL_REQUEST_TYPES`: a union of literals has no runtime value to
 * iterate, and `satisfies` is what turns a typo here into a compile error.
 *
 * **`CommentUnderReview` and `FeedbackNew` are deliberately absent.** The tray was
 * designed for four sources and the other two arrive **with the query that produces them** — a kind the
 * backend cannot emit is a badge that never renders and a `Record` case nobody can test.
 */
export const QUEUE_ITEM_KINDS = ['ApprovalRequest', 'TableWaitingReview'] as const satisfies readonly QueueItemKind[]

/**
 * The two source discriminators, which are also the first segment of the claim URL.
 *
 * They are the vocabulary of `entity_type`, reused rather than re-coined — see
 * {@link QueueItemType}.
 */
export const QUEUE_ITEM_TYPES = ['approval_request', 'game_table'] as const satisfies readonly QueueItemType[]
