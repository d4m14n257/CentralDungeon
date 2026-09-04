import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { sessionsApi } from './sessionsApi'

/**
 * The master declaring that a session was played (#195).
 *
 * It is its own action and not a side effect of recording attendance: "we played" and "these people
 * came" are two facts, and inferring one from the other would silently mark as played a session that
 * was called off.
 *
 * @param tableId the table whose calendar is invalidated
 * @returns the mutation, taking the session id
 */
export function useHoldSession(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (sessionId: string) => sessionsApi.hold(sessionId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.list(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.mine(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.master.dashboard() })
    },
  })
}
