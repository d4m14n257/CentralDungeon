import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'

/**
 * /master/tables/:id - a different hook from useGameTable on purpose (decisiones.md #152): that one
 * is public, since any player uses it on /tables/:id to decide whether to apply, so reusing it here
 * would send the table's whole detail over the network before the frontend could decide whether the
 * actor may manage it. This one hits /managed, which the backend refuses with a 403 before reading
 * anything if the actor does not run that table.
 */
export function useManagedTable(id: string) {
  return useQuery({
    queryKey: queryKeys.tables.managedDetail(id),
    queryFn: () => gameTablesApi.managedById(id),
    staleTime: staleTime.tableDetail,
  })
}
