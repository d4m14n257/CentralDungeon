import { useInfiniteQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'

/**
 * The explorer brings one page at a time and adds to it with "See more" (decisiones.md #173): it is
 * a listing to browse, not one to walk by page number. `useInfiniteQuery` keeps the pages already
 * fetched, so going back in the browser does not ask for them again.
 *
 * **The search is part of the key** and not a filter applied after the fact: changing what was typed
 * asks a different question, so the pages already accumulated for the previous one must not be
 * appended to (#116). A new query starts its own stack of pages.
 *
 * @param query the raw search box, in the language of #164, or undefined when it is empty
 */
export function useGameTables(query?: string) {
  return useInfiniteQuery({
    queryKey: queryKeys.tables.list({ q: query }),
    queryFn: ({ pageParam }) => gameTablesApi.list(pageParam, query),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined),
    staleTime: staleTime.tableList,
  })
}
