import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { usersApi } from './usersApi'

export function useMe(enabled = true) {
  return useQuery({
    queryKey: queryKeys.users.me(),
    queryFn: usersApi.me,
    staleTime: staleTime.profile,
    enabled,
  })
}
