import { serializeSearchQuery } from '@/lib/searchQuery'

import type { ApprovalRequestType, ApprovalStatus, SubmittableRequestType } from './types'

/**
 * Every kind of request there is, in the order they are offered and listed.
 *
 * A written-out tuple rather than something derived from the union, for the same reason as
 * `PLATFORM_ROLES`: a union of literals has no runtime value to iterate, and `satisfies` is what
 * turns a typo here into a compile error.
 *
 * **`TablePause` and `PlayerBan` joined in F3.4, with their producer** — which is the condition F3.2
 * wrote into this very comment when it left them out. This is the list `/request_type` offers, so
 * leaving them out now would mean the two kinds an admin most wants to find are the two they cannot
 * filter by.
 */
export const APPROVAL_REQUEST_TYPES = [
  'MasterGrant',
  'TableOpen',
  'General',
  'TablePause',
  'PlayerBan',
] as const satisfies readonly ApprovalRequestType[]

/**
 * The kinds somebody can raise from `POST /api/v1/requests`, which is not all of them.
 *
 * **A separate list because the endpoint takes no `entityId`**, deliberately, so that nobody can ask
 * in another person's name (F3.2 §0d). The three below are *about whoever is asking*, and who that
 * is comes from the token. The two F3.4 added are about something else — a table, an application —
 * so each got a route of its own hanging off the entity, where "being the master of **this** table"
 * can be checked against the thing in the path (#121).
 *
 * It is what the submit form validates against, so a screen cannot offer a kind whose only possible
 * outcome is a refusal (principio 2).
 */
export const SUBMITTABLE_REQUEST_TYPES = ['MasterGrant', 'TableOpen', 'General'] as const satisfies readonly SubmittableRequestType[]

/** Every state a request can be in, in the order of its life: asked, then resolved one way or the other. */
export const APPROVAL_STATUSES = ['Pending', 'Approved', 'Rejected'] as const satisfies readonly ApprovalStatus[]

/**
 * The query for what is still waiting: `/status Pending`.
 *
 * **Built with `serializeSearchQuery` rather than written by hand**, because it is read by two
 * parsers and has to mean the same thing to both. It is what `/admin/requests` opens filtered by
 * (#136) and what the request sections ask `/requests/mine` for, so the string leaves the frontend
 * and is parsed by `SearchQueryParser.java` — one hand-typed space or a label where a value belongs
 * and the filter silently matches nothing. The serializer is the same one the search box writes its
 * `?q=` with, which is exactly what keeps the two in step.
 *
 * On the admin side it lives here and not in the endpoint: `GET /admin/requests` with no `q` answers
 * with everything, like every other listing of the platform. What a tray opens showing is a decision
 * of the screen.
 */
export const PENDING_REQUESTS_QUERY = serializeSearchQuery([{ field: 'status', values: ['Pending'], connector: 'and' }])
