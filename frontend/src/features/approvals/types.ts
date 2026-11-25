import type { StrictOmit } from '@/types/utils'

/**
 * The kinds of request the platform knows.
 *
 * **Five, and the last two arrived with their producer** — the rule being that a value of an enum
 * that nothing emits is an orphan, so the first three were declared alone. `TablePause` is emitted by
 * `POST /game-tables/{id}/request-pause` and `PlayerBan` by `.../registrations/{id}/request-block`.
 *
 * **Neither of the two new ones can be raised from `POST /api/v1/requests`** — see
 * `SUBMITTABLE_REQUEST_TYPES` in `requestTypes.ts`. Both are *about* something other than the person
 * asking, and that endpoint takes no `entityId` on purpose.
 *
 * A union of literals and not a TS `enum`: mirror of `ApprovalRequestType`.
 */
export type ApprovalRequestType = 'MasterGrant' | 'TableOpen' | 'General' | 'TablePause' | 'PlayerBan'

/**
 * The kinds `POST /api/v1/requests` accepts: the three that are *about whoever is asking*.
 *
 * Derived with `Extract` rather than written out a second time, so that renaming one
 * above stops compiling here instead of quietly shrinking what the form offers.
 */
export type SubmittableRequestType = Extract<ApprovalRequestType, 'MasterGrant' | 'TableOpen' | 'General'>

/** Where a request stands. Mirror of `ApprovalStatus`: it is asked once and resolved once. */
export type ApprovalStatus = 'Pending' | 'Approved' | 'Rejected'

/**
 * Mirror of `ApprovalRequestDetailResponse` — one request in full, as `/admin/requests/{id}` answers
 * and as the two resolvers answer back.
 *
 * **The base type, with the listing's row derived from it below**: the detail
 * is what a resolution comes back as, so it is the shape the screen writes into its cache.
 *
 * `entityType`/`entityId` are a loose pair and not a reference to an entity, because that is the
 * price of letting the row survive whatever it points at. The three kinds raised by the asker themselves point at the
 * person who asked (`user`) — the request is about them.
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
   * Who took the request for themselves. **Null until somebody claims it**: it is
   * written by the claim endpoints of the shared tray.
   */
  claimedByName: string | null
  resolvedByName: string | null
  resolutionNote: string | null
  resolvedAt: string | null
  /**
   * The table the request is about, or null when it is about none. A pause names its table and a veto
   * reaches it through its application; the other kinds concern a person.
   */
  gameTableId: string | null
  /**
   * For a veto request, the display name of the person it would bar — an admin decides about them,
   * not about whoever asked. Null for every other kind, whose subject is the requester.
   */
  subjectName: string | null
  /**
   * For a `TableOpen`, the name the requester proposed for the table — the name of the draft that
   * approving it creates. Null for every other kind.
   */
  proposedTableName: string | null
  createdAt: string
}

/**
 * Mirror of `ApprovalRequestSummaryResponse`: one row of `/admin/requests` and one entry of the
 * reader's own list.
 *
 * Derived from {@link ApprovalRequestDetail} because it is the narrower view of the same thing — the
 * listing carries no resolution and no polymorphic reference — plus the requester's id, so their name
 * can link out.
 */
export type ApprovalRequestSummary = StrictOmit<
  ApprovalRequestDetail,
  'entityType' | 'entityId' | 'resolvedByName' | 'resolutionNote' | 'resolvedAt'
> & {
  /** Only so the requester's name can link to their page; what the row shows is `requestedByName`. */
  requestedById: string
}

/**
 * Mirror of `BanRequestResponse` — one veto a co-master is asking for.
 *
 * **Its own shape and not {@link ApprovalRequestSummary}**, because of the one thing that summary
 * cannot say: *who* is going to be vetoed. The shared row carries who asked and why, which is right
 * for the admin tray — there the entity really is the requester — and leaves a `Primary` with two
 * open requests on the same table able to tell them apart only by the wording of the reason. On a
 * screen whose entire purpose is deciding about a person, naming them is not a nicety.
 *
 * **Every field is non-null, and that is the point.** The alternative was a nullable
 * `targetUserName` on the shared summary, filled in for one type out of five — the record with half
 * its fields null, and one that would have made every other reader carry a field
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
  /** The co-master who asked. They are the one the answer is written to. */
  requestedByName: string
  /** Why they are asking, verbatim. It is the whole of what the `Primary` decides on. */
  justification: string
  createdAt: string
}

/**
 * What asking for something sends: which kind, and why.
 *
 * **No `entityId`.** The three submittable kinds are about whoever is asking, and who that is comes from
 * the token — taking it from the body would let somebody ask in another
 * person's name.
 */
export interface SubmitApprovalRequestInput {
  type: SubmittableRequestType
  justification: string
  /**
   * The name of the table a `TableOpen` asks to open: required for that kind, because approving it
   * creates a draft with this name. The server ignores it for the other kinds, so the form always
   * sends it and leaves it empty there.
   */
  tableName: string
}

/**
 * What resolving one sends: the note, which is all either act sends.
 *
 * **Required on both of them**: the justification is obligatory at the two ends of the
 * mechanism. Rejecting without saying why is half a mechanism.
 */
export interface ResolveApprovalRequestInput {
  resolutionNote: string
}
