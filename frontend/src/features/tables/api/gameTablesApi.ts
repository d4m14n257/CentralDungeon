import { api } from '@/api/client'

import type { GameTableDetail, GameTableSummary } from '../types'

export const gameTablesApi = {
  list: (page = 0) => api.getPage<GameTableSummary>('/api/v1/game-tables', { page }),
  mine: (page = 0) => api.getPage<GameTableSummary>('/api/v1/game-tables/mine', { page }),
  managed: (page = 0) => api.getPage<GameTableSummary>('/api/v1/game-tables/managed', { page }),
  byId: (id: string) => api.get<GameTableDetail>(`/api/v1/game-tables/${id}`),
  open: (id: string) => api.post<GameTableDetail>(`/api/v1/game-tables/${id}/open`),
}
