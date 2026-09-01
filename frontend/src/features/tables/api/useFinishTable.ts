import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'

export function useFinishTable(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => gameTablesApi.finish(tableId),
    onSuccess: (table) => {
      queryClient.setQueryData(queryKeys.tables.managedDetail(tableId), table)
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.statusHistory(tableId) })
    },
  })
}
