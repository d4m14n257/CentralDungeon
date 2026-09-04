import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { usersApi } from './usersApi'

/**
 * Searching for people (#164, #165). `keepPreviousData` keeps the list from flickering between
 * keystrokes: the previous results stay on screen, dimmed, until the new ones arrive.
 *
 * @param query   what was typed, in the search language of `lib/searchQuery.ts`
 * @param enabled whether to ask at all — an empty box does not search
 * @param tableId when given, search only among the people who could be made a master of that
 *                table. It is a different endpoint because it is authorized differently: the plain
 *                directory answers to an admin, the scoped one to the table's own master (#165)
 * @returns the query for the matching people
 */
export function useUserSearch(query: string, enabled: boolean, tableId?: string) {
  return useQuery({
    queryKey: queryKeys.users.search(query, tableId),
    queryFn: () => (tableId ? usersApi.searchMasterCandidates(tableId, query) : usersApi.search(query)),
    enabled,
    staleTime: staleTime.profile,
    placeholderData: keepPreviousData,
  })
}
