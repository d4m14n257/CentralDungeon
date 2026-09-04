import { api } from '@/api/client'
import { pageSize } from '@/config/pagination'

import type {
  AddMasterRequest,
  AdminTableSummary,
  AssignMastersRequest,
  ChangeTableStatusRequest,
  CreateGameTableRequest,
  GameTableDetail,
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
  list: (page = 0, size = pageSize.explorer) => api.getPage<GameTableSummary>('/api/v1/game-tables', { page, size }),
  mine: (page = 0, size = pageSize.list) => api.getPage<GameTableSummary>('/api/v1/game-tables/mine', { page, size }),
  managed: (page = 0, size = pageSize.list) => api.getPage<GameTableSummary>('/api/v1/game-tables/managed', { page, size }),
  admin: (statuses?: GameTableStatus[], page = 0, size = pageSize.adminQueue) =>
    api.getPage<AdminTableSummary>('/api/v1/game-tables/admin', { status: statuses?.join(','), page, size }),
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
  start: (id: string) => api.post<GameTableDetail>(`/api/v1/game-tables/${id}/start`),
  finish: (id: string) => api.post<GameTableDetail>(`/api/v1/game-tables/${id}/finish`),
  cancel: (id: string, request: ChangeTableStatusRequest) =>
    api.post<GameTableDetail, ChangeTableStatusRequest>(`/api/v1/game-tables/${id}/cancel`, request),
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
