import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { tasksApi } from './tasksApi'
import type { CreateTaskInput } from '../types'

/**
 * Publishing a task, which is also what notifies its recipients (#77).
 *
 * Two branches are invalidated: the master's board, and the reader's applicable list — a master who
 * also plays somewhere is one person with two screens, and the second would otherwise keep showing
 * the state from before.
 *
 * @param tableId the table doing the asking
 * @returns the mutation, taking what is being asked and of whom
 */
export function usePublishTask(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateTaskInput) => tasksApi.publish(tableId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tasks.table(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tasks.applicable(tableId) })
    },
  })
}
