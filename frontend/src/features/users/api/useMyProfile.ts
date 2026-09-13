import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { usersApi } from './usersApi'

/**
 * The reader's own profile screen, `/player/profile` (#248): name, country, roles and aggregate
 * attendance.
 *
 * Deliberately not {@link import('./useMe').useMe}: that one feeds the app shell — which contexts
 * the switcher offers, whether onboarding still blocks, `hasManagedTables` — and never carries
 * attendance, because the shell does not need it on every page load. Two different questions to the
 * backend, even though today they describe the same account.
 *
 * @returns the query for the caller's own profile
 */
export function useMyProfile() {
  return useQuery({
    queryKey: queryKeys.profiles.mine(),
    queryFn: usersApi.myProfile,
    staleTime: staleTime.profile,
  })
}
