import { api } from '@/api/client'
import { pageSize } from '@/config/pagination'

import type {
  AddMasterRequest,
  AdminTableSummary,
  AssignMastersRequest,
  ChangeTableStatusRequest,
  CreateGameTableRequest,
  GameTableDetail,
  GameTableHistoryEntry,
  GameTableStatus,
  GameTableSummary,
  MasterDashboard,
  MasterSummary,
  TableStatusChange,
  TableType,
  UpdateGameTableRequest,
} from '../types'

/**
 * The table types the wizard's selector offers. Its own object because it is a different resource:
 * /api/v1/table-types is a catalog of the platform, not something that hangs off a table.
 */
export const tableTypesApi = {
  list: () => api.getPage<TableType>('/api/v1/table-types', { page: 0, size: 50 }),
}

/** Every call about a game table: the listings, the detail, and each lifecycle transition. */
export const gameTablesApi = {
  /** The explorer. `q` is the search language of #164: a bare term is the table's name, and
   *  `/table_system`, `/table_tag` and `/table_platform` resolve through synonym groups (#246). */
  list: (page = 0, query?: string, size = pageSize.explorer) =>
    api.getPage<GameTableSummary>('/api/v1/game-tables', { page, size, q: query }),
  mine: (page = 0, size = pageSize.list) => api.getPage<GameTableSummary>('/api/v1/game-tables/mine', { page, size }),
  /** `/player/history` (#133): every table of theirs that stopped being active, `Finished` or
   *  `Canceled`, most recently closed first. Its own endpoint and not a filter on `mine` above:
   *  that one now answers only for the live tables (#133), so a history read must not compete with
   *  it for the same cache entry. */
  history: (page = 0, size = pageSize.list) => api.getPage<GameTableHistoryEntry>('/api/v1/game-tables/mine/history', { page, size }),
  managed: (page = 0, size = pageSize.list) => api.getPage<GameTableSummary>('/api/v1/game-tables/managed', { page, size }),
  /**
   * `/admin/tables`: **every table there is** since F3.3 (#176), not the ones waiting on a review.
   *
   * The listing used to default to the review statuses, which made it a second tray with rules of its
   * own; what waits on a review now belongs to `/admin/queue`, and this answers "which tables exist
   * and what state is each one in" — a question that needs all of them and a search box.
   *
   * `q` is the search language of #164 with the four explorer commands plus `/table_status` and
   * `/table_master`. `status` stays as a separate parameter: it is not something the reader types,
   * it is a caller narrowing the listing programmatically.
   */
  admin: (query?: string, statuses?: GameTableStatus[], page = 0, size = pageSize.adminQueue) =>
    api.getPage<AdminTableSummary>('/api/v1/game-tables/admin', { q: query, status: statuses?.join(','), page, size }),
  byId: (id: string) => api.get<GameTableDetail>(`/api/v1/game-tables/${id}`),
  /** Only a table that was never public; the backend refuses the rest (#175). */
  delete: (id: string) => api.delete(`/api/v1/game-tables/${id}`),
  managedById: (id: string) => api.get<GameTableDetail>(`/api/v1/game-tables/${id}/managed`),
  statusHistory: (id: string) => api.get<TableStatusChange[]>(`/api/v1/game-tables/${id}/status-history`),
  create: (request: CreateGameTableRequest) => api.post<GameTableDetail, CreateGameTableRequest>('/api/v1/game-tables', request),
  /** A full replacement, not a patch: an absent field empties it, which is how the agenda gets cleared. */
  update: (id: string, request: UpdateGameTableRequest) =>
    api.put<GameTableDetail, UpdateGameTableRequest>(`/api/v1/game-tables/${id}`, request),
  createUnassigned: (request: CreateGameTableRequest) =>
    api.post<GameTableDetail, CreateGameTableRequest>('/api/v1/game-tables/unassigned', request),
  assignMasters: (id: string, request: AssignMastersRequest) =>
    api.post<GameTableDetail, AssignMastersRequest>(`/api/v1/game-tables/${id}/assign-masters`, request),
  approve: (id: string) => api.post<GameTableDetail>(`/api/v1/game-tables/${id}/approve`),
  requestChanges: (id: string, request: ChangeTableStatusRequest) =>
    api.post<GameTableDetail, ChangeTableStatusRequest>(`/api/v1/game-tables/${id}/request-changes`, request),
  resubmit: (id: string) => api.post<GameTableDetail>(`/api/v1/game-tables/${id}/resubmit`),
  submitForReview: (id: string) => api.post<GameTableDetail>(`/api/v1/game-tables/${id}/submit`),
  start: (id: string) => api.post<GameTableDetail>(`/api/v1/game-tables/${id}/start`),
  finish: (id: string) => api.post<GameTableDetail>(`/api/v1/game-tables/${id}/finish`),
  cancel: (id: string, request: ChangeTableStatusRequest) =>
    api.post<GameTableDetail, ChangeTableStatusRequest>(`/api/v1/game-tables/${id}/cancel`, request),
  /**
   * A master **asking** for a pause (#32, F3.4): `InProgress → PauseRequested`, plus the
   * `TablePause` request an admin answers.
   *
   * **Its own route on the table and not `POST /api/v1/requests`**, deliberately: that endpoint takes
   * no `entityId` so that nobody can ask in another person's name (F3.2), and here the entity is not
   * the person asking. With the table in the path, "being the master of *this* table" is checked
   * where it belongs (#121).
   *
   * Refused with `PAUSE_ALREADY_REQUESTED` when a pause is already waiting on an answer — which the
   * screen does not offer, so it only surfaces for a second tab or another route in.
   *
   * @param id      the table
   * @param request the reason, required (#32)
   */
  requestPause: (id: string, request: ChangeTableStatusRequest) =>
    api.post<GameTableDetail, ChangeTableStatusRequest>(`/api/v1/game-tables/${id}/request-pause`, request),
  /**
   * An admin pausing a table outright, with the reason that goes on the record (#32).
   *
   * **The endpoint has existed since E2 and had no screen until F3.4** (#163). Pausing does not touch
   * `table_sessions`: the freeze is derived on read, because a pause is reversible.
   *
   * @param id      the table
   * @param request the reason, required
   */
  pause: (id: string, request: ChangeTableStatusRequest) =>
    api.post<GameTableDetail, ChangeTableStatusRequest>(`/api/v1/game-tables/${id}/pause`, request),
  /**
   * Bringing a paused table back, rescheduling from today and keeping the numbering (#33, #193).
   *
   * **No justification, and that is not an oversight**: #32 demands one for `Pause` and `Canceled`,
   * and resuming is the return to normal.
   *
   * **It can be refused with `409 SCHEDULE_CONFLICT`**, because the master may have committed to
   * something else while the table was paused — and the refusal carries the other table's name in
   * `errorParams.otherTableName`, which is what lets the screen say *which* one (#193, #197).
   *
   * @param id the table
   */
  resume: (id: string) => api.post<GameTableDetail>(`/api/v1/game-tables/${id}/resume`),
  /** Adds a co-master or hands the table over; answers with the masters afterwards (#73). */
  addMaster: (id: string, request: AddMasterRequest) =>
    api.post<MasterSummary[], AddMasterRequest>(`/api/v1/game-tables/${id}/masters`, request),
  /** Removes a co-master. The row is marked, not dropped (#175); the answer is the list to re-render. */
  removeMaster: (id: string, userId: string) => api.delete<MasterSummary[]>(`/api/v1/game-tables/${id}/masters/${userId}`),
}

/**
 * The master's work tray (#136). Its own object because it is a different resource:
 * /api/v1/master/dashboard is about the person, not about any one table.
 */
export const masterDashboardApi = {
  get: () => api.get<MasterDashboard>('/api/v1/master/dashboard'),
}
