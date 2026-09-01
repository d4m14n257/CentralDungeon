import { useMutation, useQueryClient } from '@tanstack/react-query'

import { gameTablesApi } from './gameTablesApi'

export function useApproveTable() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (tableId: string) => gameTablesApi.approve(tableId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tables', 'admin'] })
    },
  })
}
