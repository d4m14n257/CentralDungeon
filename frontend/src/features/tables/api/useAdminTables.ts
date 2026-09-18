import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'
import type { GameTableStatus } from '../types'

/**
 * `/admin/tables`: every table the platform has, whatever its state (#176, F3.3).
 *
 * A working list, so it pages by number with `keepPreviousData` instead of "load more" (#173) — the
 * rows are worked through, not browsed.
 *
 * **It gained `query` when the listing stopped being a queue.** Showing every table is only useful
 * with a way to find one, which is what the six search commands of `adminTableSearchFields` are for;
 * `statuses` stays for a caller that narrows the listing itself rather than for something the reader
 * types.
 *
 * @param query    the search box, already debounced, in the language of `lib/searchQuery.ts`
 * @param statuses the statuses to narrow to, when the caller and not the reader is narrowing
 * @param page     zero-based page number
 * @returns the query for that page
 */
export function useAdminTables(query?: string, statuses?: GameTableStatus[], page = 0) {
  return useQuery({
    queryKey: queryKeys.tables.admin(query, statuses, page),
    queryFn: () => gameTablesApi.admin(query || undefined, statuses, page),
    staleTime: staleTime.tableList,
    placeholderData: keepPreviousData,
  })
}
