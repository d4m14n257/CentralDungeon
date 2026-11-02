import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { usersApi } from './usersApi'

/**
 * The tables a person played to the end, one page at a time - what opens from the finished-tables
 * count of somebody else's profile.
 *
 * The backend answers 404 for anyone who may not read that profile, so the dialog that uses it shows
 * the same error as any failed read. The previous page stays on screen while the next one loads.
 *
 * @param id      whose finished tables
 * @param page    zero-based page
 * @param enabled whether to ask at all; false while the dialog is closed
 * @returns the query for that page
 */
export function useFinishedTables(id: string, page: number, enabled = true) {
  return useQuery({
    queryKey: queryKeys.profiles.finishedTables(id, page),
    queryFn: () => usersApi.finishedTables(id, page),
    staleTime: staleTime.profile,
    placeholderData: keepPreviousData,
    enabled: enabled && id.length > 0,
  })
}
