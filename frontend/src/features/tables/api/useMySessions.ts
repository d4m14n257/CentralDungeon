import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { sessionsApi } from './sessionsApi'

/**
 * My calendar and my attendance on a table, for `/my/tables/:id`.
 *
 * Both halves are about the actor of the token and there is no parameter that could name anybody
 * else, which is what makes them impossible to ask for on someone's behalf (#121).
 *
 * @param tableId the table
 * @returns the query for my sessions and my attendance summary
 */
export function useMySessions(tableId: string) {
  return useQuery({
    queryKey: queryKeys.sessions.mine(tableId),
    queryFn: () => sessionsApi.mine(tableId),
    staleTime: staleTime.tableDetail,
  })
}
