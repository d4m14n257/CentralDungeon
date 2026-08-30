import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { registrationsApi } from './registrationsApi'

export function useAcceptRegistration(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (registrationId: string) => registrationsApi.accept(registrationId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.registrations.candidates(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.detail(tableId) })
    },
  })
}
