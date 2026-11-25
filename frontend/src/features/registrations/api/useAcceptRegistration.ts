import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { registrationsApi } from './registrationsApi'

/**
 * Accepts a candidate.
 *
 * Taking the last seat auto-rejects everyone still queued, so this invalidates the whole
 * table branch rather than patching one row - one accept can change several.
 *
 * @param tableId the table, for the invalidation
 * @returns the mutation, taking the registration's id
 */
export function useAcceptRegistration(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (registrationId: string) => registrationsApi.accept(registrationId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.registrations.candidates(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.detail(tableId) })
      // The master's own view and the admin listing show the seats and the call for players, which a full table turns off.
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.managedDetail(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.adminAll() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.master.dashboard() })
    },
  })
}
