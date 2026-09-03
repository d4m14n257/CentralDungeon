import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'
import type { UpdateGameTableRequest } from '../types'

/**
 * Rewrites a table its master still owns - the wizard's second pass, and how a draft sent back with
 * `ChangesRequested` is corrected.
 *
 * Both detail queries are invalidated, not just the managed one: the same table is read from
 * /tables/:id as well, and leaving that cache holding the previous agenda is how somebody applies
 * to a Tuesday that no longer exists.
 *
 * @param id the table being edited
 * @returns the mutation, taking the whole table as it should end up
 */
export function useUpdateTable(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: UpdateGameTableRequest) => gameTablesApi.update(id, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.managedDetail(id) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.detail(id) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.managed() })
    },
  })
}
