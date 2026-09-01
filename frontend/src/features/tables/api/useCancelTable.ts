import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'
import type { ChangeTableStatusRequest } from '../types'

export function useCancelTable(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: ChangeTableStatusRequest) => gameTablesApi.cancel(tableId, request),
    onSuccess: (table) => {
      queryClient.setQueryData(queryKeys.tables.managedDetail(tableId), table)
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.statusHistory(tableId) })
    },
  })
}
