import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { tasksApi } from './tasksApi'
import type { CreateSubmissionInput } from '../types'

/**
 * Handing in an answer. **Every call inserts a new one** (#76) — nothing is overwritten, so there is
 * no optimistic update to write and no cached answer to patch in place.
 *
 * Three branches are invalidated: my own answers to that task, the applicable list (its
 * `mySubmissionCount` just changed), and the reuse history, because a file handed in counts as used
 * and its `lastUsedAt` moved (#75).
 *
 * @param tableId the table the task belongs to
 * @returns the mutation, taking the task and the answer
 */
export function useSubmitTask(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ taskId, input }: { taskId: string; input: CreateSubmissionInput }) => tasksApi.submit(taskId, input),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tasks.mine(variables.taskId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tasks.submissions(variables.taskId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tasks.applicable(tableId) })
      void queryClient.invalidateQueries({ queryKey: ['files', 'mine'] })
    },
  })
}
