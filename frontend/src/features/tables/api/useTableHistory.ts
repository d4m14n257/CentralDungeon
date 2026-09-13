import { useInfiniteQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'

/**
 * The tables the signed-in person's run at is over - `Finished` or `Canceled` - with their final
 * attendance. Backs `/player/history` (#133).
 *
 * A reading list, like the explorer, so it accumulates with "See more" instead of numbered pages
 * (decisiones.md #173): a career's worth of closed tables has no working-list reason to jump around
 * by page number, and `useInfiniteQuery` keeps the pages already fetched when the reader navigates
 * away and back.
 *
 * `staleTime.tableList`, the same policy as `useMyTables`: a closed table never reopens, but a table
 * that just closed has to land here without a long wait, so this is not treated as immutable.
 *
 * @returns the infinite query for the reader's history, most recently closed first
 */
export function useTableHistory() {
  return useInfiniteQuery({
    queryKey: queryKeys.tables.history(),
    queryFn: ({ pageParam }) => gameTablesApi.history(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined),
    staleTime: staleTime.tableList,
  })
}
