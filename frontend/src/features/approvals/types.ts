import type { StrictOmit } from '@/types/utils'

/**
 * The kinds of request the platform knows (#42, #90).
 *
 * **Five since F3.4, and the last two arrived with their producer** — which is the rule F3.2 set
 * when it deliberately declared three: a value of an enum that nothing emits is an orphan, and this
 * phase exists partly to close the ones F1.7 surveyed. `TablePause` is emitted by
 * `POST /game-tables/{id}/request-pause` and `PlayerBan` by `.../registrations/{id}/request-block`.
 *
 * **Neither of the two new ones can be raised from `POST /api/v1/requests`** — see
 * `SUBMITTABLE_REQUEST_TYPES` in `requestTypes.ts`. Both are *about* something other than the person
 * asking, and that endpoint takes no `entityId` on purpose.
 *
 * A union of literals and not a TS `enum` (arquitectura.md §3.2): mirror of `ApprovalRequestType`.
 */
export type ApprovalRequestType = 'MasterGrant' | 'TableOpen' | 'General' | 'TablePause' | 'PlayerBan'

/**
 * The kinds `POST /api/v1/requests` accepts: the three that are *about whoever is asking*.
 *
 * Derived with `Extract` rather than written out a second time (regla dura 6), so that renaming one
 * above stops compiling here instead of quietly shrinking what the form offers.
 */
export type SubmittableRequestType = Extract<ApprovalRequestType, 'MasterGrant' | 'TableOpen' | 'General'>

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
 * Mirror of `BanRequestResponse` — one veto a co-master is asking for (#39, F3.4).
 *
 * **Its own shape and not {@link ApprovalRequestSummary}**, because of the one thing that summary
 * cannot say: *who* is going to be vetoed. The shared row carries who asked and why, which is right
 * for the admin tray — there the entity really is the requester — and leaves a `Primary` with two
 * open requests on the same table able to tell them apart only by the wording of the reason. On a
 * screen whose entire purpose is deciding about a person, naming them is not a nicety.
 *
 * **Every field is non-null, and that is the point.** The alternative was a nullable
 * `targetUserName` on the shared summary, filled in for one type out of five — the record with half
 * its fields null that R3 forbids, and one that would have made every other reader carry a field
 * that is always null for them. Two questions, two shapes.
 */
export interface BanRequest {
  /** The request, which is what approving or refusing addresses. */
  requestId: string
  /** The application the veto is about — what the roster row is keyed by, so the two line up. */
  registrationId: string
  targetUserId: string
  /** The field this shape exists for: a decision about a person is made by name, not by id. */
  targetUserName: string
  /** The co-master who asked. They are the one the answer is written to (#42). */
  requestedByName: string
  /** Why they are asking, verbatim. It is the whole of what the `Primary` decides on. */
  justification: string
  createdAt: string
}

/**
 * What asking for something sends: which kind, and why (#42).
 *
 * **No `entityId`.** The three kinds of F3.2 are about whoever is asking, and who that is comes from
 * the token (arquitectura.md §2.6) — taking it from the body would let somebody ask in another
 * person's name.
 */
export interface SubmitApprovalRequestInput {
  type: SubmittableRequestType
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
