import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'

/**
 * A table's lifecycle history: who moved it, where to, and why. Masters of the table and admins
 * only, which the backend enforces - the reasons behind a refusal are written between them.
 *
 * @param id the table
 * @returns the query for its status history
 */
export function useTableStatusHistory(id: string) {
  return useQuery({
    queryKey: queryKeys.tables.statusHistory(id),
    queryFn: () => gameTablesApi.statusHistory(id),
    staleTime: staleTime.tableDetail,
  })
}
