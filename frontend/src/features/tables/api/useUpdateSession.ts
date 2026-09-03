import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { sessionsApi } from './sessionsApi'
import type { UpdateSessionRequest } from '../types'

/**
 * The master correcting a session: its date, its notes, or both.
 *
 * It invalidates the whole calendar instead of patching the one row: moving a date changes how the
 * run reads, and the server is what knows what is still pending afterwards.
 *
 * @param tableId the table whose calendar is invalidated
 * @returns the mutation, taking the session id and the new fields
 */
export function useUpdateSession(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ sessionId, request }: { sessionId: string; request: UpdateSessionRequest }) => sessionsApi.update(sessionId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.list(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.mine(tableId) })
    },
  })
}
