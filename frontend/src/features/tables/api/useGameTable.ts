import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'

/**
 * One table's public detail, for /tables/:id.
 *
 * @param id the table to read
 * @returns the query for its detail
 */
export function useGameTable(id: string) {
  return useQuery({
    queryKey: queryKeys.tables.detail(id),
    queryFn: () => gameTablesApi.byId(id),
    staleTime: staleTime.tableDetail,
  })
}
