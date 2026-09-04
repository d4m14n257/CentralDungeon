import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { usersApi } from './usersApi'

/**
 * Searching for people (#164, #165). `keepPreviousData` keeps the list from flickering between
 * keystrokes: the previous results stay on screen, dimmed, until the new ones arrive.
 */
export function useUserSearch(query: string, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.users.search(query),
    queryFn: () => usersApi.search(query),
    enabled,
    staleTime: staleTime.profile,
    placeholderData: keepPreviousData,
  })
}
