import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'
import type { ChangeTableStatusRequest } from '../types'

/**
 * Ends a table early, with a reason on the record. Either its master or an admin may - which of the
 * two the caller is gets decided by the backend, not here.
 *
 * @returns the mutation, taking the table's id and the justification
 */
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
