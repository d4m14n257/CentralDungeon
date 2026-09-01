import { useMutation, useQueryClient } from '@tanstack/react-query'

import { gameTablesApi } from './gameTablesApi'
import type { ChangeTableStatusRequest } from '../types'

export function useRequestChanges() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ tableId, request }: { tableId: string; request: ChangeTableStatusRequest }) =>
      gameTablesApi.requestChanges(tableId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tables', 'admin'] })
    },
  })
}
