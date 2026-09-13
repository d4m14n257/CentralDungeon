import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { usersApi } from './usersApi'

/**
 * Somebody else's profile, `/player/users/:id` (#41, #44, #47).
 *
 * The backend answers **404** — never 403, never a 200 with fields missing — for anyone the actor
 * cannot currently see, whether the id never existed or the two-week visibility window of #44 has
 * closed (#249). `UserProfilePage` is what turns that 404 into a softer explanation on screen; this
 * hook only carries the status through, exactly like `error.status === 403` does elsewhere for a
 * veto (#150).
 *
 * @param id the profile to read
 * @returns the query for that profile
 */
export function useUserProfile(id: string) {
  return useQuery({
    queryKey: queryKeys.profiles.detail(id),
    queryFn: () => usersApi.profile(id),
    staleTime: staleTime.profile,
    enabled: id.length > 0,
  })
}
