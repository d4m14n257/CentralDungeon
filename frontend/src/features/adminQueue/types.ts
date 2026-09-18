/**
 * Which table a tray item came out of (#100).
 *
 * **The same vocabulary of strings `entity_type` (#78) and `Notification.relatedEntityType` already
 * use**, and not a third one invented here: the value travels in the claim URL
 * (`/admin-queue/{type}/{id}/claim`), so a spelling of its own would be a second name for a thing
 * that already has one.
 *
 * A union of literals and not a TS `enum` (arquitectura.md §3.2).
 */
export type AdminQueueItemType = 'approval_request' | 'game_table'

/**
 * What kind of work the item is — the fine discriminator, mirror of `AdminQueueItemKind`.
 *
 * Two of them in F3.3 and not four: the comments under review and the untriaged system feedback are
 * F5's, and they arrive with the query that produces them. A value of an enum nothing can emit is a
 * filter that always answers nothing.
 *
 * It crosses HTTP, so the backend pins it with `@JsonValue` over these exact names (#253).
 */
export type AdminQueueItemKind = 'ApprovalRequest' | 'TableWaitingReview'

/**
 * Mirror of `AdminQueueItemResponse` — one row of the shared admin tray, whatever it came from.
 *
 * **One flat shape for every source, which is the whole point of the tray** (#100): an admin opens
 * one screen and sees everything waiting on them, ordered by who has been waiting longest (#136),
 * rather than checking four listings and guessing which is most overdue.
 *
 * `id` is the id of the row **in its own table**, not an id of the tray: there is no `admin_queue`
 * table and there is not going to be one (#11). The pair `(type, id)` is what addresses an item.
 *
 * Names and never ids on the people involved, like every other admin listing: a tray nobody can read
 * without a second lookup is a tray nobody reads.
 */
export interface AdminQueueItem {
  type: AdminQueueItemType
  id: string
  kind: AdminQueueItemKind
  /** The table's name, or what the request is asking for. Already a sentence a person can read. */
  title: string
  /** Who provoked the item — the requester, or the master who sent the table in. */
  requestedByName: string
  /** The justification of a request. Null for a table: sending one in carries no words. */
  detail: string | null
  /** Since when it has been waiting. The tray's order, and the only urgency it has (#136). */
  waitingSince: string
  /**
   * Who took the item for themselves, or null while nobody has (#100).
   *
   * **A non-null value here always means the reader**: the listing only ever returns what is free or
   * what belongs to whoever asked, so a name in this field is their own. That is what
   * {@link isClaimedByReader} rests on, and why the tray never has to know the reader's own name.
   *
   * **Null does not mean "not yours to resolve"** — it means nobody is on it yet, and resolving it
   * takes it. What a reservation buys is that the row drops out of everybody else's tray while
   * somebody works on it, not permission to work on it.
   */
  claimedByName: string | null
  /** When they took it. What the fifteen-minute release is counted from. */
  claimedAt: string | null
}

/**
 * Whether the reader is already holding this item.
 *
 * **It is not a permission check, and it never should have read like one.** Resolving an item does
 * not require holding it: an item nobody holds is resolved on the spot, and doing so reserves it
 * implicitly (#100). What this answers is narrower and purely presentational — does the row say
 * "Tomar" or "Soltar", and does the chip say it is free or that somebody is on it.
 *
 * Written as a function over the row rather than compared against the reader's own name, because the
 * server already answered the question: `GET /admin-queue` returns `claimed_by IS NULL OR
 * claimed_by = :actor` and nothing else (#100), so a claim that is visible at all is the reader's.
 * Matching names on this side would be a second, worse implementation of a rule that is enforced
 * where it belongs — and it would break on two people sharing a display name.
 *
 * **That filter is this function's whole premise, and it is a rule of the endpoint, not of the
 * shape.** Today it is written once per source — `ApprovalRequestRepository` and
 * `GameTableRepository` both carry the `claimed_by is null or claimed_by.id = :actorId` clause. **A
 * source added in F5 without it would not make this function wrong so much as make it lie by its own
 * name**: it would start answering "somebody has this" while calling itself "the reader has this",
 * and the tray would offer "Soltar" on a colleague's row — a button whose only possible answer is
 * `ITEM_ALREADY_CLAIMED`. Whoever adds `comments` or `system_feedback` to the tray checks that
 * clause before touching anything here.
 *
 * @param item the row
 * @returns whether the reader is the one holding the reservation
 */
export function isClaimedByReader(item: AdminQueueItem): boolean {
  // A truthy check and not `!== null`: the answer to "is this reserved" must not be "yes" because a
  // field arrived as an empty string, and an unreserved row is the safe reading either way - it
  // offers taking it, which the backend answers idempotently.
  return Boolean(item.claimedByName)
}
