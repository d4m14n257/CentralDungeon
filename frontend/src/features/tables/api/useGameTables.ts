import { useInfiniteQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'

/**
 * The explorer brings one page at a time and adds to it with "See more" (decisiones.md #173): it is
 * a listing to browse, not one to walk by page number. `useInfiniteQuery` keeps the pages already
 * fetched, so going back in the browser does not ask for them again.
 */
export function useGameTables() {
  return useInfiniteQuery({
    queryKey: queryKeys.tables.list(),
    queryFn: ({ pageParam }) => gameTablesApi.list(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined),
    staleTime: staleTime.tableList,
  })
}
