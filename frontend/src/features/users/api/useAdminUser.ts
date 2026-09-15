import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { adminUsersApi } from './adminUsersApi'

/**
 * One account as an administrator sees it: where it stands right now, whatever its status.
 *
 * Its own query rather than reading the row out of the listing's cache, because the two are answers
 * to different questions: a row of the table is what matched a search on some page, and this is
 * "what is true about this person" at the moment somebody opened their record.
 *
 * @param id      the account to read
 * @param enabled whether to ask at all — the panel that reads it is closed most of the time
 * @returns the query for that account
 */
export function useAdminUser(id: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.users.adminDetail(id),
    queryFn: () => adminUsersApi.byId(id),
    staleTime: staleTime.profile,
    enabled,
  })
}
