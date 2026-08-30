import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { registrationsApi } from './registrationsApi'

export function useMyApplications(page = 0) {
  return useQuery({
    queryKey: queryKeys.registrations.mine(),
    queryFn: () => registrationsApi.mine(page),
  })
}
