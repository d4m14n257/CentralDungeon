import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'

/**
 * /master/tables/:id - distinto de useGameTable a propósito (decisiones.md #152): ese es
 * público (cualquier jugador lo usa en /tables/:id para decidir si postularse), así que
 * reusarlo acá mandaría el detalle completo de la mesa por la red antes de que el frontend
 * pudiera decidir si el actor tiene permiso de gestionarla. Este pega a /managed, que el
 * backend rechaza con 403 antes de leer nada si el actor no es master de esa mesa.
 */
export function useManagedTable(id: string) {
  return useQuery({
    queryKey: queryKeys.tables.managedDetail(id),
    queryFn: () => gameTablesApi.managedById(id),
    staleTime: staleTime.tableDetail,
  })
}
