import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'
import type { UpdateGameTableRequest } from '../types'

/**
 * Rewrites a table as an admin, from /admin/tables/:id/edit (#284). The backend tells every master
 * of the table that it happened.
 *
 * It invalidates what {@link useUpdateTable} does - the table is read from the master's screens and
 * from the player's, and both caches would keep showing the old text - plus the admin listing, whose
 * row carries the name and the capacity.
 *
 * @param id the table being edited
 * @returns the mutation, taking the whole table as it should end up
 */
export function useAdminUpdateTable(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: UpdateGameTableRequest) => gameTablesApi.adminUpdate(id, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.managedDetail(id) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.detail(id) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.managed() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.adminAll() })
    },
  })
}
