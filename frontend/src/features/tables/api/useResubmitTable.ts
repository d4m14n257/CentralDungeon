import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'

/**
 * The master sending a corrected draft back for review.
 *
 * @returns the mutation, taking the table's id
 */
export function useResubmitTable(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => gameTablesApi.resubmit(tableId),
    onSuccess: (table) => {
      queryClient.setQueryData(queryKeys.tables.managedDetail(tableId), table)
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.statusHistory(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.master.dashboard() })
    },
  })
}
