import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { registrationsApi } from './registrationsApi'

interface RequestBlockVariables {
  registrationId: string
  justification: string
}

/**
 * A co-master **asking** for a veto (#39, #71).
 *
 * **Nothing about the table changes yet**, which is the whole difference from `useBlockPlayer`: the
 * person keeps playing, keeps seeing the table and keeps downloading its files until the `Primary`
 * answers. So this invalidates only the list of pending veto requests — invalidating the roster here
 * would suggest something moved, and nothing did.
 *
 * **And the `Primary` is who answers it, not an admin.** #39 is specific where #90 is generic, and a
 * veto between a co-master and a player of *that* table is decided by whoever runs it rather than by
 * the platform. The request is therefore kept out of the shared admin tray, which is also why this
 * hook does not invalidate `adminQueue`: the row was never going to appear there.
 *
 * @param tableId the table
 * @returns the mutation, taking the application and the reason the `Primary` will read
 */
export function useRequestPlayerBlock(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ registrationId, justification }: RequestBlockVariables) =>
      registrationsApi.requestBlock(tableId, registrationId, justification),
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.requests.banRequests(tableId) })
      // What the co-master themselves asked for, on the screen that lists their own requests.
      void queryClient.invalidateQueries({ queryKey: queryKeys.requests.mineAll() })
    },
  })
}
