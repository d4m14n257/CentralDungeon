import { useInfiniteQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'

/**
 * The tables the signed-in person plays at - an accepted registration, not a master row. Backs
 * /player/my-tables, one page at a time with "Ver más" (ADR-04 §6), like the explorer.
 *
 * @returns the infinite query for the tables they play at
 */
export function useMyTables() {
  return useInfiniteQuery({
    queryKey: queryKeys.tables.mine(),
    queryFn: ({ pageParam }) => gameTablesApi.mine(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined),
    staleTime: staleTime.tableList,
  })
}
