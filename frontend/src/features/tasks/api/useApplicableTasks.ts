import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { tasksApi } from './tasksApi'

/**
 * What this table is asking of **me**, for `/tables/:id` and `/my/tables/:id`.
 *
 * The answer depends on the reader — whether they play there, and who a `Single` task names — so it
 * has a cache entry of its own rather than riding in the table's detail (#121). Two people looking at
 * the same table see two different lists, and they must not share one entry.
 *
 * @param tableId the table
 * @returns the query for the tasks that apply to the reader
 */
export function useApplicableTasks(tableId: string) {
  return useQuery({
    queryKey: queryKeys.tasks.applicable(tableId),
    queryFn: () => tasksApi.listApplicable(tableId),
    staleTime: staleTime.tasks,
  })
}
