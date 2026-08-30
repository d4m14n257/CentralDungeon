import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { registrationsApi } from './registrationsApi'

interface RejectVariables {
  registrationId: string
  justification: string
}

export function useRejectRegistration(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ registrationId, justification }: RejectVariables) => registrationsApi.reject(registrationId, justification),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.registrations.candidates(tableId) })
    },
  })
}
