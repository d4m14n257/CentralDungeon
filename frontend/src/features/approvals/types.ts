import type { StrictOmit } from '@/types/utils'

/**
 * The kinds of request the platform knows (#42, #90).
 *
 * **Three and not five.** The baseline DDL comments `TablePause` and `PlayerBan` too, but nothing
 * produces either of them until F3.4 — and a value of an enum that nothing emits is the orphan this
 * slice was written to avoid. They arrive with their producer.
 *
 * A union of literals and not a TS `enum` (arquitectura.md §3.2): mirror of `ApprovalRequestType`.
 */
export type ApprovalRequestType = 'MasterGrant' | 'TableOpen' | 'General'

/** Where a request stands. Mirror of `ApprovalStatus`: it is asked once and resolved once. */
export type ApprovalStatus = 'Pending' | 'Approved' | 'Rejected'

/**
 * Mirror of `ApprovalRequestDetailResponse` — one request in full, as `/admin/requests/{id}` answers
 * and as the two resolvers answer back.
 *
 * **The base type, with the listing's row derived from it below** (arquitectura.md §3.2): the detail
 * is what a resolution comes back as, so it is the shape the screen writes into its cache.
 *
 * `entityType`/`entityId` are a loose pair and not a reference to an entity, because that is the
 * price of #78: the row survives whatever it points at. In F3.2 all three kinds point at the person
 * who asked (`user`), which is true of every one of them — the request is about them.
 *
 * Names and never ids on the people involved: a queue nobody can read without a second lookup is a
 * queue nobody reads.
 */
export interface ApprovalRequestDetail {
  id: string
  type: ApprovalRequestType
  status: ApprovalStatus
  entityType: string
  entityId: string
  requestedByName: string
  justification: string
  /**
   * Who took the request for themselves. **Always null in F3.2**: the columns exist (#100) and the
   * claim endpoints are F3.3's, so nothing writes it yet.
   */
  claimedByName: string | null
  resolvedByName: string | null
  resolutionNote: string | null
  resolvedAt: string | null
  createdAt: string
}

/**
 * Mirror of `ApprovalRequestSummaryResponse`: one row of `/admin/requests` and one entry of the
 * reader's own list.
 *
 * Derived from {@link ApprovalRequestDetail} because it is exactly the narrower view of the same
 * thing — the listing carries no resolution and no polymorphic reference, and nothing else differs.
 */
export type ApprovalRequestSummary = StrictOmit<
  ApprovalRequestDetail,
  'entityType' | 'entityId' | 'resolvedByName' | 'resolutionNote' | 'resolvedAt'
>

/**
 * What asking for something sends: which kind, and why (#42).
 *
 * **No `entityId`.** The three kinds of F3.2 are about whoever is asking, and who that is comes from
 * the token (arquitectura.md §2.6) — taking it from the body would let somebody ask in another
 * person's name.
 */
export interface SubmitApprovalRequestInput {
  type: ApprovalRequestType
  justification: string
}

/**
 * What resolving one sends: the note, which is all either act sends.
 *
 * **Required on both of them** (#42): the justification is obligatory at the two ends of the
 * mechanism. Rejecting without saying why is half a mechanism.
 */
export interface ResolveApprovalRequestInput {
  resolutionNote: string
}
