import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'
import type { AddMasterRequest } from '../types'

/**
 * Adds a co-master to a table, or hands the table over to one of them.
 *
 * Asking for `Primary` promotes the target and demotes whoever held it: a table has exactly one
 * (#73). Only the current holder may do either, and the backend is what says so — membership, not
 * the platform role (#135).
 *
 * @param tableId the table whose masters change
 * @returns the mutation, taking who to add or promote and as what
 */
export function useAddMaster(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: AddMasterRequest) => gameTablesApi.addMaster(tableId, request),
    onSuccess: () => {
      // The masters ride inside the table's detail rather than on a branch of their own, so this is
      // what re-renders the section. The tray follows: gaining or losing a table changes what is
      // waiting for the reader.
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.managedDetail(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.master.dashboard() })
    },
  })
}
