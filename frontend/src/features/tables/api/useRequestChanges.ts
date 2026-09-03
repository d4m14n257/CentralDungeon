import { useMutation, useQueryClient } from '@tanstack/react-query'

import { gameTablesApi } from './gameTablesApi'
import type { ChangeTableStatusRequest } from '../types'

/**
 * Sends a draft back to its master with a reason. The reason is required and is what the master
 * reads on the status tab.
 *
 * @returns the mutation, taking the table's id and the justification
 */
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
