import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { sessionsApi } from './sessionsApi'
import type { TableSession } from '../types'

/**
 * El master cancelando una sesión — y la mesa recuperándola al final (#194).
 *
 * La respuesta es el calendario entero porque la cancelación y su reposición son un solo cambio, así
 * que se escribe directo en la caché en vez de invalidar y volver a pedir.
 *
 * @param tableId la mesa del calendario
 * @returns la mutación, que toma el id de la sesión a cancelar
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
