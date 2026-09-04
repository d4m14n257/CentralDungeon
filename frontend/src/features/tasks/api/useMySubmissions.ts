import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { tasksApi } from './tasksApi'

/**
 * My own answers to a task — what I already handed in, oldest first.
 *
 * A list rather than "the latest one", because answers accumulate and none replaces another (#76):
 * showing only the last would quietly claim the earlier ones stopped counting.
 *
 * Nothing is fetched for a task nobody opened: `CollapsibleSection` does not render its children
 * while closed, so the block holding this hook is not mounted until the reader expands the row.
 *
 * @param taskId the task
 * @returns the query for their own answers
 */
export function useMySubmissions(taskId: string) {
  return useQuery({
    queryKey: queryKeys.tasks.mine(taskId),
    queryFn: () => tasksApi.listMySubmissions(taskId),
    staleTime: staleTime.tasks,
  })
}
