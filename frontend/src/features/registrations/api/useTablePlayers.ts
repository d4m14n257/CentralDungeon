import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { registrationsApi } from './registrationsApi'

/**
 * The table's current players, for the people running it.
 *
 * Its own branch and not a filter over the candidate queue: the queue's order is a fairness rule
 * (#28) and a roster's is not, and one hook answering both questions is how a caller ends up relying
 * on the wrong ordering.
 *
 * @param tableId the table
 * @param enabled whether the screen actually needs the roster — the picker only asks for it when a
 *                task is being addressed to one person
 * @returns the query for its players
 */
export function useTablePlayers(tableId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.registrations.players(tableId),
    queryFn: () => registrationsApi.players(tableId),
    enabled,
  })
}
