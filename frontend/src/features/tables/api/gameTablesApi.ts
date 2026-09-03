import { api } from '@/api/client'
import { pageSize } from '@/config/pagination'

import type {
  AdminTableSummary,
  AssignMastersRequest,
  ChangeTableStatusRequest,
  CreateGameTableRequest,
  GameTableDetail,
  GameTableStatus,
  GameTableSummary,
  TableStatusChange,
} from '../types'

/** Every call about a game table: the listings, the detail, and each lifecycle transition. */
export const gameTablesApi = {
  list: (page = 0, size = pageSize.explorer) => api.getPage<GameTableSummary>('/api/v1/game-tables', { page, size }),
  mine: (page = 0, size = pageSize.list) => api.getPage<GameTableSummary>('/api/v1/game-tables/mine', { page, size }),
  managed: (page = 0, size = pageSize.list) => api.getPage<GameTableSummary>('/api/v1/game-tables/managed', { page, size }),
  admin: (statuses?: GameTableStatus[], page = 0, size = pageSize.adminQueue) =>
    api.getPage<AdminTableSummary>('/api/v1/game-tables/admin', { status: statuses?.join(','), page, size }),
  byId: (id: string) => api.get<GameTableDetail>(`/api/v1/game-tables/${id}`),
  /** Solo una mesa que nunca fue pública; el backend rechaza el resto (#175). */
  delete: (id: string) => api.delete(`/api/v1/game-tables/${id}`),
  managedById: (id: string) => api.get<GameTableDetail>(`/api/v1/game-tables/${id}/managed`),
  statusHistory: (id: string) => api.get<TableStatusChange[]>(`/api/v1/game-tables/${id}/status-history`),
  create: (request: CreateGameTableRequest) => api.post<GameTableDetail, CreateGameTableRequest>('/api/v1/game-tables', request),
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
}
