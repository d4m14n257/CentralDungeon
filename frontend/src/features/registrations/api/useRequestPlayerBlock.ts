import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { registrationsApi } from './registrationsApi'

interface RequestBlockVariables {
  registrationId: string
  justification: string
}

/**
 * A master of the table **asking** the admins for a veto.
 *
 * **Nothing about the table changes yet**: the person keeps playing, keeps seeing the table and keeps
 * downloading its files until an admin approves. So this invalidates only the table's pending veto
 * requests and the asker's own requests — invalidating the roster here would suggest something
 * moved, and nothing did. The admin's tray picks the new row up on its next read.
 *
 * @param tableId the table
 * @returns the mutation, taking the application and the reason the admin will read
 */
export function useRequestPlayerBlock(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ registrationId, justification }: RequestBlockVariables) =>
      registrationsApi.requestBlock(tableId, registrationId, justification),
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.requests.banRequests(tableId) })
      // What the master themselves asked for, on the screen that lists their own requests.
      void queryClient.invalidateQueries({ queryKey: queryKeys.requests.mineAll() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminQueue.all() })
    },
  })
}
