import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'

/**
 * The tables the signed-in person plays at - an accepted registration, not a master row. Backs
 * /my/tables.
 *
 * @returns the query for the tables they play at
 */
export function useMyTables(page = 0) {
  return useQuery({
    queryKey: queryKeys.tables.mine(),
    queryFn: () => gameTablesApi.mine(page),
    staleTime: staleTime.tableList,
  })
}
