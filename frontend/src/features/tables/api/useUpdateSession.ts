import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { sessionsApi } from './sessionsApi'
import type { UpdateSessionRequest } from '../types'

/**
 * El master corrigiendo una sesión: su fecha, sus notas, o las dos.
 *
 * Invalida el calendario entero y no parchea la fila: mover una fecha reordena cómo se lee la
 * tanda, y el servidor es quien sabe qué quedó pendiente después.
 *
 * @param tableId la mesa del calendario que se invalida
 * @returns la mutación, que toma el id de la sesión y los campos nuevos
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
