import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { tasksApi } from './tasksApi'

/**
 * What came in for one task and who has not answered, for the people running the table.
 *
 * Nothing is fetched for a task nobody opened, and no flag is needed to arrange that:
 * `CollapsibleSection` does not render its children while closed, so the panel holding this hook is
 * not mounted until somebody expands the row.
 *
 * @param taskId the task
 * @returns the query for its answers and its roster of missing people
 */
export function useTaskSubmissions(taskId: string) {
  return useQuery({
    queryKey: queryKeys.tasks.submissions(taskId),
    queryFn: () => tasksApi.listSubmissions(taskId),
    staleTime: staleTime.tasks,
  })
}
