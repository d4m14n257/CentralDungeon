import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'

/** /master/tables: every table the actor is a master of, any status (pertenencia, not the platform role - #17). */
export function useManagedTables(page = 0) {
  return useQuery({
    queryKey: queryKeys.tables.managed(),
    queryFn: () => gameTablesApi.managed(page),
    staleTime: staleTime.tableList,
  })
}
