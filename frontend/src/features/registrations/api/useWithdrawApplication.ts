import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { registrationsApi } from './registrationsApi'

/**
 * Takes back one's own pending application.
 *
 * It is the action R4's clash notice points at (#178): being told that two of your tables now fall
 * at the same hour is only useful if there is something you can do about it.
 *
 * @returns the mutation, taking the application's id
 */
export function useWithdrawApplication() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (registrationId: string) => registrationsApi.withdraw(registrationId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.registrations.mine() })
      // The explorer's cards carry "ya te postulaste" and the clash warning, and both just changed.
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.list() })
    },
  })
}
