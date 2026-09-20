import { useMutation, useQueryClient } from '@tanstack/react-query'

import { registrationsApi } from './registrationsApi'
import { invalidateAfterVeto } from './vetoInvalidation'

interface UnblockVariables {
  registrationId: string
  justification: string
}

/**
 * Lifting a veto (#39).
 *
 * **This is the half that makes the veto acceptable**, and the reason the vetoed row keeps its place
 * in the roster: an action that only exists on a row nobody renders is an action nobody can take.
 *
 * The reason is required here too, because what the platform records is not the veto but the
 * **change** — a `blocked` flag could never say that it was lifted, by whom, or how many times, and
 * #39 asks for exactly that so a pattern of impulsive vetoes is visible.
 *
 * Refused with `NOT_PRIMARY_MASTER` (403) to a co-master and `REGISTRATION_NOT_BLOCKED` (409) on a
 * row somebody else already restored.
 *
 * @param tableId the table
 * @returns the mutation, taking the application and the reason
 */
export function useUnblockPlayer(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ registrationId, justification }: UnblockVariables) => registrationsApi.unblock(tableId, registrationId, justification),
    meta: { showsItsOwnError: true },
    onSuccess: () => invalidateAfterVeto(queryClient, tableId),
  })
}
