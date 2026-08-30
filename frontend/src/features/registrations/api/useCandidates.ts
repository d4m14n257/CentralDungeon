import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { registrationsApi } from './registrationsApi'

export function useCandidates(tableId: string, page = 0) {
  return useQuery({
    queryKey: queryKeys.registrations.candidates(tableId),
    queryFn: () => registrationsApi.candidates(tableId, page),
  })
}
