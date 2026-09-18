import { api } from '@/api/client'
import { pageSize } from '@/config/pagination'

import type { ApprovalRequestDetail, ApprovalRequestSummary, ResolveApprovalRequestInput, SubmitApprovalRequestInput } from '../types'

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
