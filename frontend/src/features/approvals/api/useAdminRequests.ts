import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { pageSize } from '@/config/pagination'
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
 * @param size  rows per page, one of `adminPageSizeOptions` (#271)
 * @returns the query for that page
 */
export function useAdminRequests(query: string, page: number, size: number = pageSize.admin) {
  return useQuery({
    queryKey: queryKeys.requests.admin(query, page, size),
    queryFn: () => approvalsApi.list(query || undefined, page, size),
    staleTime: staleTime.profile,
    placeholderData: keepPreviousData,
  })
}
