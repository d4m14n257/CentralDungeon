import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { registrationsApi } from './registrationsApi'

interface RejectVariables {
  registrationId: string
  justification: string
}

/**
 * Turns down a candidate, with the reason that reaches them.
 *
 * @param tableId the table, for the invalidation
 * @returns the mutation, taking the registration's id and the justification
 */
export function useRejectRegistration(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ registrationId, justification }: RejectVariables) => registrationsApi.reject(registrationId, justification),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.registrations.candidates(tableId) })
    },
  })
}
