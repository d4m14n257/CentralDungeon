import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'

/**
 * The master sending their draft to review for the first time (#245).
 *
 * It is the act that makes the table exist for anybody else — and the one that takes it out of their
 * hands until an admin answers, which is why the screen asks before doing it.
 *
 * @param tableId the table
 * @returns the mutation, taking nothing
 */
export function useSubmitTableForReview(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => gameTablesApi.submitForReview(tableId),
    onSuccess: (table) => {
      queryClient.setQueryData(queryKeys.tables.managedDetail(tableId), table)
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.statusHistory(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.master.dashboard() })
    },
  })
}
