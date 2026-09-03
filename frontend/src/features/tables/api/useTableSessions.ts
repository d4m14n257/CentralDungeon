import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { sessionsApi } from './sessionsApi'

/**
 * A table's calendar as the people running it see it, with notes and rosters.
 *
 * While the table is in `Pause` the backend does not return the pending sessions (#32, #33): a pause
 * promises no dates, so the screen shows what was played and what was called off, and nothing else.
 *
 * @param tableId the table
 * @returns the query for its calendar
 */
export function useTableSessions(tableId: string) {
  return useQuery({
    queryKey: queryKeys.sessions.list(tableId),
    queryFn: () => sessionsApi.forTable(tableId),
    staleTime: staleTime.tableDetail,
  })
}
