import { useMutation, useQueryClient } from '@tanstack/react-query'

import { gameTablesApi } from './gameTablesApi'

/**
 * An admin approving a draft - the only road from Preparation to Opened (#27).
 *
 * @returns the mutation, taking the table's id
 */
export function useApproveTable() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (tableId: string) => gameTablesApi.approve(tableId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tables', 'admin'] })
    },
  })
}
