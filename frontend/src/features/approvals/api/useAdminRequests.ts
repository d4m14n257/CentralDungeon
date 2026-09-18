import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { approvalsApi } from './approvalsApi'

/**
 * The `/admin/requests` tray: every request the platform has, whatever its state.
 *
 * A working list, so it pages by number and keeps the previous page on screen while the next one
 * arrives: without that, the table blinks to a skeleton on every click (#173).
 *
 * **The `Pending` filter is not in here.** What the tray opens showing is a decision of the screen,
 * written into its `?q=` — see `PENDING_REQUESTS_QUERY` — so this hook stays what its name says: the
 * listing, for whatever was searched.
 *
 * @param query the search box, already debounced
 * @param page  zero-based page number
 * @returns the query for that page
 */
export function useAdminRequests(query: string, page: number) {
  return useQuery({
    queryKey: queryKeys.requests.admin(query, page),
    queryFn: () => approvalsApi.list(query || undefined, page),
    staleTime: staleTime.profile,
    placeholderData: keepPreviousData,
  })
}
