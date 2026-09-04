import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'

/**
 * Removes a co-master from a table.
 *
 * The row is marked and not dropped (#175) — who ran a table stays on the record — but membership
 * stops counting it at once, so the person loses the table's screens immediately.
 *
 * The table's own master cannot be removed: handing the role over comes first, and the backend
 * answers 409 for the attempt.
 *
 * @param tableId the table whose masters change
 * @returns the mutation, taking the id of the person to remove
 */
export function useRemoveMaster(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => gameTablesApi.removeMaster(tableId, userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.managedDetail(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.master.dashboard() })
    },
  })
}
