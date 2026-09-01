import { useMutation, useQueryClient } from '@tanstack/react-query'

import { gameTablesApi } from './gameTablesApi'
import type { AssignMastersRequest } from '../types'

export function useAssignMasters() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ tableId, request }: { tableId: string; request: AssignMastersRequest }) =>
      gameTablesApi.assignMasters(tableId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tables', 'admin'] })
    },
  })
}
