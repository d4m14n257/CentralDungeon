import { api } from '@/api/client'
import { pageSize } from '@/config/pagination'

import type {
  ApprovalRequestDetail,
  ApprovalRequestSummary,
  BanRequest,
  ResolveApprovalRequestInput,
  SubmitApprovalRequestInput,
} from '../types'

/**
 * The six calls of the request mechanism (#42), as one module.
 *
 * **They answer to two audiences and that is visible in the paths**: `/api/v1/requests` is the side
 * of whoever asks, authenticated and with no role behind it, while `/api/v1/admin/requests` is the
 * side of whoever resolves and refuses anybody who is not an admin or the owner. One module for both
 * because they are one mechanism — the summary a person reads about their own request is the very
 * same row the tray shows.
 *
 * **The actor is never a parameter.** Who is asking and who is resolving both come from the token,
 * and an id in the body would be a claim the caller makes about themselves (arquitectura.md §2.6).
 * It is also why {@link submit} sends no `entityId`: the three kinds of F3.2 are about the person
 * who asked.
 *
 * **The two resolvers answer with the updated detail** rather than `204`, which is what lets a caller
 * confirm the outcome — the note, who sealed it and when are only readable in that answer. The hooks
 * on top of it still re-read the branch instead of patching one row in: approving a `MasterGrant`
 * moves a role this listing never asked about.
 */
export const approvalsApi = {
  /**
   * Asks for something (#42). Refused with `REQUEST_ALREADY_PENDING` when one of the same kind is
   * still waiting, and with `MASTER_ROLE_ALREADY_HELD` when the master role is asked for by somebody
   * who has it — neither of which the screens offer, so both only surface for another route in.
   *
   * @param input which kind, and why
   */
  submit: (input: SubmitApprovalRequestInput) => api.post<ApprovalRequestDetail, SubmitApprovalRequestInput>('/api/v1/requests', input),

  /**
   * What the reader has asked for, and how each one went.
   *
   * **This is what keeps a request from disappearing.** Without it the screen that prompted the
   * request would have no way to say "you already asked, it is waiting" and would offer the button
   * again — a button whose only possible outcome is a `409`.
   *
   * It speaks the same search language as the tray, with the same three commands — and **the actor
   * is not one of them**: the service forces the requester from the token and combines it with
   * `AND`, so whatever the query says can only narrow further. `/requested_by somebody-else` is
   * syntactically valid and finds strictly less; there is no spelling of `q` that reaches another
   * person's requests.
   *
   * Newest first, unlike the admin tray: this is somebody looking at what they just asked for, not a
   * queue being worked through from the oldest.
   *
   * @param query the search query, canonical — `PENDING_REQUESTS_QUERY` for the sections that ask
   *              whether something is still waiting
   * @param page  zero-based page number
   */
  mine: (query: string | undefined, page = 0) =>
    api.getPage<ApprovalRequestSummary>('/api/v1/requests/mine', { q: query, page, size: pageSize.list }),

  /**
   * The `/admin/requests` tray: every request there is, whatever its state.
   *
   * @param query the search box, already debounced, in the language of `lib/searchQuery.ts`
   * @param page  zero-based page number
   */
  list: (query: string | undefined, page = 0) =>
    api.getPage<ApprovalRequestSummary>('/api/v1/admin/requests', { q: query, page, size: pageSize.adminQueue }),

  /**
   * One request, in full — which is where the resolution is: who sealed it, when, and the note they
   * wrote. The listing's row carries none of that.
   *
   * @param id the request to read
   */
  byId: (id: string) => api.get<ApprovalRequestDetail>(`/api/v1/admin/requests/${id}`),

  /**
   * Approves a request. What that does depends on the kind, and it is the service that decides:
   * `MasterGrant` grants the role through `UserRoleService`, the same single road `/admin/users`
   * uses; `TableOpen` records that the request stands and **does not create the table** — the request
   * carries no name, no system, no seats and no agenda; `General` has no effect beyond being closed.
   *
   * @param id    the request to approve
   * @param input the note, which is required
   */
  approve: (id: string, input: ResolveApprovalRequestInput) =>
    api.post<ApprovalRequestDetail, ResolveApprovalRequestInput>(`/api/v1/admin/requests/${id}/approve`, input),

  /**
   * Rejects a request. The note is required here too (#42): a refusal nobody explained is the half
   * that makes the mechanism worthless to whoever asked.
   *
   * @param id    the request to reject
   * @param input the note, which is required
   */
  reject: (id: string, input: ResolveApprovalRequestInput) =>
    api.post<ApprovalRequestDetail, ResolveApprovalRequestInput>(`/api/v1/admin/requests/${id}/reject`, input),
}

/**
 * A body this feature deliberately does not read.
 *
 * **`unknown` and not `void`**, because `void` would be a claim about the wire that is false: the
 * endpoint answers with a full `RegistrationResponse`. What is true is that *this* feature cannot
 * model it — a registration is `features/registrations`' vocabulary and a feature never imports from
 * another (regla dura 16) — so the honest type is the project's own answer for what is not known
 * here (arquitectura.md §3.2: never `any`, `unknown` for the unknown). Callers re-read the lists
 * they render rather than patching a row in from an answer they cannot type.
 */
type UnreadBody = unknown

/**
 * The veto requests of one table, which **an admin does not resolve** (#39, F3.4).
 *
 * **Its own module, and that separation is the decision rather than a filing convention.** Two
 * written decisions meet here and pull opposite ways: #90 is generic — `approval_requests` covers
 * "todo pedido dirigido a los admins" — and #39 is specific: a veto is applied by the table's
 * `Primary`, and a co-master "necesita aprobación del `Primary`". The specific one wins, because a
 * veto between a co-master and a player of *that* table is decided by whoever runs it, not by the
 * platform. So these rows are kept out of the shared admin tray entirely, and the three calls below
 * hang off the table rather than off `/admin/requests`, where the reader would be the wrong person.
 *
 * `TablePause` goes the other way and stays with the admins: pausing a table is a platform act, and
 * a master asking for one is asking somebody above them.
 */
export const banRequestsApi = {
  /**
   * The veto requests still waiting on this table, for whoever runs it.
   *
   * Readable by every master of the table and not only the `Primary`: a co-master who asked for a
   * veto has to be able to see that it is still waiting, or they will ask twice.
   *
   * **It answers with `BanRequest` and not with the shared summary**, so each line names the person
   * it is about — which is the one thing the summary could not say, and the thing a `Primary` with
   * two open requests needs in order to tell them apart. A list and not a page: it is bounded by how
   * many people are at one table.
   *
   * @param tableId the table
   */
  list: (tableId: string) => api.get<BanRequest[]>(`/api/v1/game-tables/${tableId}/ban-requests`),

  /**
   * Granting a co-master's veto request: the veto is applied in the same transaction (#39).
   *
   * **What comes back is a `RegistrationResponse`, and it is deliberately not read here** — see
   * {@link UnreadBody}. That is `features/registrations`' vocabulary and a feature never imports
   * from another (regla dura 16); the roster is re-read instead, which is the list the screen
   * actually renders.
   *
   * @param tableId   the table
   * @param requestId the request being granted
   * @param input     the note, required at both ends of the mechanism (#42)
   */
  approve: (tableId: string, requestId: string, input: ResolveApprovalRequestInput) =>
    api.post<UnreadBody, ResolveApprovalRequestInput>(`/api/v1/game-tables/${tableId}/ban-requests/${requestId}/approve`, input),

  /**
   * Turning a co-master's veto request down. Nothing about the table changes; the note is what the
   * co-master reads, so it is required here too.
   *
   * @param tableId   the table
   * @param requestId the request being refused
   * @param input     the note, required
   */
  reject: (tableId: string, requestId: string, input: ResolveApprovalRequestInput) =>
    api.post<ApprovalRequestDetail, ResolveApprovalRequestInput>(`/api/v1/game-tables/${tableId}/ban-requests/${requestId}/reject`, input),
}
