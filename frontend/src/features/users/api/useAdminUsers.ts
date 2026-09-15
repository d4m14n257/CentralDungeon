import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
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
 * @returns the query for that page
 */
export function useAdminUsers(query: string, page: number) {
  return useQuery({
    queryKey: queryKeys.users.admin(query, page),
    queryFn: () => adminUsersApi.list(query || undefined, page),
    staleTime: staleTime.profile,
    placeholderData: keepPreviousData,
  })
}
