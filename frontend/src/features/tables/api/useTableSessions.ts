import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { sessionsApi } from './sessionsApi'

/**
 * El calendario de una mesa como lo ve quien la dirige, con notas y padrón.
 *
 * Mientras la mesa está en `Pause` el backend no devuelve las sesiones pendientes (#32, #33): una
 * pausa no promete fechas, así que la pantalla muestra lo jugado y lo cancelado, y nada más.
 *
 * @param tableId la mesa
 * @returns la query del calendario
 */
export function useTableSessions(tableId: string) {
  return useQuery({
    queryKey: queryKeys.sessions.list(tableId),
    queryFn: () => sessionsApi.forTable(tableId),
    staleTime: staleTime.tableDetail,
  })
}
