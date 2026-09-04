import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { tasksApi } from './tasksApi'
import type { UpdateTaskInput } from '../types'

/**
 * Correcting a published task. It does not notify again — #77 puts the notification at publication,
 * and a request fixed three times must not ring three times.
 *
 * @param tableId the table the task belongs to, for the branches to invalidate
 * @returns the mutation, taking the task and the whole state it should end in (#189)
 */
export function useUpdateTask(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ taskId, input }: { taskId: string; input: UpdateTaskInput }) => tasksApi.update(taskId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tasks.table(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tasks.applicable(tableId) })
    },
  })
}
