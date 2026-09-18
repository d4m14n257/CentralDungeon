import { serializeSearchQuery } from '@/lib/searchQuery'

import type { ApprovalRequestType, ApprovalStatus } from './types'

/**
 * The three kinds of request F3.2 has, in the order they are offered and listed.
 *
 * A written-out tuple rather than something derived from the union, for the same reason as
 * `PLATFORM_ROLES`: a union of literals has no runtime value to iterate, and `satisfies` is what
 * turns a typo here into a compile error.
 *
 * `TablePause` and `PlayerBan` are deliberately absent — F3.4 adds them **with their producer**.
 */
export const APPROVAL_REQUEST_TYPES = ['MasterGrant', 'TableOpen', 'General'] as const satisfies readonly ApprovalRequestType[]

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
