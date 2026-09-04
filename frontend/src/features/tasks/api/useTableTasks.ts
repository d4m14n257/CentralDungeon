import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { tasksApi } from './tasksApi'

/**
 * The table's whole board, as the people running it see it — the Peticiones tab.
 *
 * Its own query and not part of the table's detail: it carries the counts and the roster size, which
 * a candidate reading the same table has no business receiving.
 *
 * @param tableId the table
 * @returns the query for its tasks
 */
export function useTableTasks(tableId: string) {
  return useQuery({
    queryKey: queryKeys.tasks.table(tableId),
    queryFn: () => tasksApi.listForTable(tableId),
    staleTime: staleTime.tasks,
  })
}
