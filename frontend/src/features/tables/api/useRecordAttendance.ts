import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { sessionsApi } from './sessionsApi'
import type { RecordAttendanceRequest } from '../types'

/**
 * The master recording who was at a session (#36).
 *
 * The roster is saved as a whole: that is how it is filled in on screen, and a half-saved roster
 * would be a state the screen then has to explain.
 *
 * @param tableId the table whose calendar is invalidated
 * @returns the mutation, taking the session id and the whole roster
 */
export function useRecordAttendance(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ sessionId, request }: { sessionId: string; request: RecordAttendanceRequest }) =>
      sessionsApi.recordAttendance(sessionId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.list(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.mine(tableId) })
    },
  })
}
