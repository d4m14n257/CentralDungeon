import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { sessionsApi } from './sessionsApi'
import type { RecordAttendanceRequest } from '../types'

/**
 * El master registrando quién estuvo en una sesión (#36).
 *
 * El padrón se guarda entero de una vez: así se completa en pantalla, y un padrón a medio guardar
 * sería un estado que la pantalla tendría que explicar.
 *
 * @param tableId la mesa del calendario que se invalida
 * @returns la mutación, que toma el id de la sesión y el padrón completo
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
