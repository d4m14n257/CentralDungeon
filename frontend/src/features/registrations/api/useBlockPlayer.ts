import { useMutation, useQueryClient } from '@tanstack/react-query'

import { registrationsApi } from './registrationsApi'
import { invalidateAfterVeto } from './vetoInvalidation'

interface BlockVariables {
  registrationId: string
  justification: string
}

/**
 * The veto, applied by the table's `Primary` (#29, #39).
 *
 * **What it does is bigger than the row it changes**: the person stops seeing this table anywhere —
 * the explorer, the detail, the sessions, the requests, and the files they used to download. Which
 * is why it invalidates far more than the roster (see `invalidateAfterVeto`).
 *
 * **It is per table and not per person.** The same account may be playing at four other tables and
 * nothing about those changes; #29 chose it that way on purpose, and it is what makes a veto a
 * master's decision rather than a moderation one.
 *
 * Refused with `NOT_PRIMARY_MASTER` (403) to a co-master, `REGISTRATION_ALREADY_BLOCKED` (409) on
 * somebody already vetoed. Neither is offered by the screen: the first is a different button for a
 * co-master and the second is a row that already shows as vetoed — so both surface only in the race
 * between two masters acting at once, which is exactly what a screen cannot prevent.
 *
 * @param tableId the table the veto is for
 * @returns the mutation, taking the application and the reason
 */
export function useBlockPlayer(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ registrationId, justification }: BlockVariables) => registrationsApi.block(tableId, registrationId, justification),
    /** The dialog stays open over the refusal, so it is shown there and not in a toast (#197). */
    meta: { showsItsOwnError: true },
    onSuccess: () => invalidateAfterVeto(queryClient, tableId),
  })
}
