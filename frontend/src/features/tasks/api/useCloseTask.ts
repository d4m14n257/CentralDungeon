import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { tasksApi } from './tasksApi'

/**
 * Closing the intake of a task. What came in stays readable — closing ends the intake, it does not
 * erase the history (#76).
 *
 * @param tableId the table the task belongs to, for the branches to invalidate
 * @returns the mutation, taking the task to close
 */
export function useCloseTask(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (taskId: string) => tasksApi.close(taskId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tasks.table(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tasks.applicable(tableId) })
    },
  })
}
