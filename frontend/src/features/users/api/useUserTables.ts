import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { usersApi } from './usersApi'

/**
 * The tables a person runs, plays at or applied to, one page at a time - the "Mesas" section of an
 * admin's view of their record (#284). Admins only: the backend refuses anybody else.
 *
 * The previous page stays on screen while the next one loads, so paging does not blank the list.
 *
 * @param id   whose tables
 * @param page zero-based page
 * @returns the query for that page
 */
export function useUserTables(id: string, page: number) {
  return useQuery({
    queryKey: queryKeys.profiles.tables(id, page),
    queryFn: () => usersApi.tables(id, page),
    staleTime: staleTime.profile,
    placeholderData: keepPreviousData,
    enabled: id.length > 0,
  })
}
