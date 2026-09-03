import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { usersApi } from './usersApi'

/**
 * The signed-in person's own profile. The whole shell is built from it: which contexts the switcher
 * offers, whether onboarding still blocks, the avatar and the karma.
 *
 * Its `roles` are for **deciding what to show, never what to allow** (#103) - authorization is the
 * backend's, endpoint by endpoint.
 *
 * @returns the query for the caller's profile
 */
export function useMe(enabled = true) {
  return useQuery({
    queryKey: queryKeys.users.me(),
    queryFn: usersApi.me,
    staleTime: staleTime.profile,
    enabled,
  })
}
