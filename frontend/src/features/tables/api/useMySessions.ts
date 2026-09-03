import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { sessionsApi } from './sessionsApi'

/**
 * Mi calendario y mi asistencia en una mesa, para `/my/tables/:id`.
 *
 * Las dos mitades son sobre el actor del token y no hay parámetro que pueda nombrar a otra persona,
 * que es lo que hace imposible pedirlas en nombre de nadie (#121).
 *
 * @param tableId la mesa
 * @returns la query de mis sesiones y mi resumen de asistencia
 */
export function useMySessions(tableId: string) {
  return useQuery({
    queryKey: queryKeys.sessions.mine(tableId),
    queryFn: () => sessionsApi.mine(tableId),
    staleTime: staleTime.tableDetail,
  })
}
