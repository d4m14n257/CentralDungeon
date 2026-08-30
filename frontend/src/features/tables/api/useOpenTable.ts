import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'

export function useOpenTable(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => gameTablesApi.open(tableId),
    onSuccess: (table) => {
      queryClient.setQueryData(queryKeys.tables.detail(tableId), table)
    },
  })
}
