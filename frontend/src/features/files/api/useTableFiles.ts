import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { filesApi } from './filesApi'

/**
 * Everything attached to a table, as the people running it see it — private attachments included.
 *
 * Its own query and not part of the table's detail: what a master sees includes what the table keeps
 * private, while the detail carries only what it shares. Two different answers, and sharing a cache
 * entry between them is how a player ends up reading a master's notes (#79).
 *
 * @param tableId the table
 * @returns the query for its attachments
 */
export function useTableFiles(tableId: string) {
  return useQuery({
    queryKey: queryKeys.files.table(tableId),
    queryFn: () => filesApi.listForTable(tableId),
    staleTime: staleTime.files,
  })
}
