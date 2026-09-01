import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'
import type { GameTableStatus } from '../types'

/** /admin/tables - sin filtro de pertenencia; el default de statuses lo decide el backend (mesas esperando revisión). */
export function useAdminTables(statuses?: GameTableStatus[], page = 0) {
  return useQuery({
    queryKey: queryKeys.tables.admin(statuses),
    queryFn: () => gameTablesApi.admin(statuses, page),
    staleTime: staleTime.tableList,
  })
}
