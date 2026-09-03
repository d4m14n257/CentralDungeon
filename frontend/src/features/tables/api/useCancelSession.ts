import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { sessionsApi } from './sessionsApi'
import type { TableSession } from '../types'

/**
 * The master calling off a session — and the table getting it back at the end (#194).
 *
 * The response is the whole calendar, because the cancellation and its replacement are one change,
 * so it is written straight into the cache instead of invalidating and asking again.
 *
 * @param tableId the table's calendar
 * @returns the mutation, taking the id of the session to call off
 */
export function useCancelSession(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (sessionId: string) => sessionsApi.cancel(sessionId),
    onSuccess: (sessions: TableSession[]) => {
      queryClient.setQueryData(queryKeys.sessions.list(tableId), sessions)
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.mine(tableId) })
    },
  })
}
