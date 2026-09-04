import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'
import type { GameTableStatus } from '../types'

/** /admin/tables - unfiltered by membership; the backend decides the default statuses (the tables waiting on a review). */
export function useAdminTables(statuses?: GameTableStatus[], page = 0) {
  return useQuery({
    queryKey: queryKeys.tables.admin(statuses, page),
    queryFn: () => gameTablesApi.admin(statuses, page),
    staleTime: staleTime.tableList,
    // The previous page stays in view while the next one arrives: without this the list flashes
    // "loading" on every Next (decisiones.md #173).
    placeholderData: keepPreviousData,
  })
}
