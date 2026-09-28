import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { pageSize } from '@/config/pagination'
import { staleTime } from '@/config/query'

import { adminUsersApi } from './adminUsersApi'

/**
 * The `/admin/users` table: every account of the platform, blocked ones included — which is what
 * separates it from `GET /api/v1/users/search`, the picker's endpoint, which only ever sees allowed
 * accounts.
 *
 * A working list, so it pages by number and keeps the previous page on screen while the next one
 * arrives: without that, the table blinks to a skeleton on every click (#173).
 *
 * @param query the search box, already debounced
 * @param page  zero-based page number
 * @param size  rows per page, one of `adminPageSizeOptions` (#271)
 * @returns the query for that page
 */
export function useAdminUsers(query: string, page: number, size: number = pageSize.admin) {
  return useQuery({
    queryKey: queryKeys.users.admin(query, page, size),
    queryFn: () => adminUsersApi.list(query || undefined, page, size),
    staleTime: staleTime.profile,
    placeholderData: keepPreviousData,
  })
}
