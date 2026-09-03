import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { sessionsApi } from './sessionsApi'

/**
 * El master declarando que una sesión se jugó (#195).
 *
 * Es una acción propia y no un efecto de registrar la asistencia: «jugamos» y «vino esta gente» son
 * dos hechos, y deducir uno del otro dejaría marcada como jugada una sesión que se suspendió.
 *
 * @param tableId la mesa del calendario que se invalida
 * @returns la mutación, que toma el id de la sesión
 */
export function useHoldSession(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (sessionId: string) => sessionsApi.hold(sessionId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.list(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.mine(tableId) })
    },
  })
}
