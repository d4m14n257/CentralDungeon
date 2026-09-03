import { useMutation, useQueryClient } from '@tanstack/react-query'

import { gameTablesApi } from './gameTablesApi'
import type { AssignMastersRequest } from '../types'

/**
 * Hands an unassigned table its first masters, which opens it directly - review is skipped because
 * an admin already vouched for it by creating it (#72).
 *
 * @returns the mutation, taking the table's id and who runs it
 */
export function useAssignMasters() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ tableId, request }: { tableId: string; request: AssignMastersRequest }) => gameTablesApi.assignMasters(tableId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tables', 'admin'] })
    },
  })
}
