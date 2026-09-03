import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { registrationsApi } from './registrationsApi'

/**
 * Everything the signed-in person applied to, whatever came of it. Backs /my/applications.
 *
 * @returns the query for their applications
 */
export function useMyApplications(page = 0) {
  return useQuery({
    queryKey: queryKeys.registrations.mine(),
    queryFn: () => registrationsApi.mine(page),
  })
}
