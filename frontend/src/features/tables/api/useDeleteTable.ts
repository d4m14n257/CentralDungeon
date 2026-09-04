import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'

/**
 * Deleting the draft of a table that was never public (decisiones.md #175). There is no detail to
 * update afterwards: the table stops existing for everybody, so its cache entry is dropped and the
 * listings it was in are invalidated.
 */
export function useDeleteTable(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => gameTablesApi.delete(tableId),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: queryKeys.tables.managedDetail(tableId) })
      queryClient.removeQueries({ queryKey: queryKeys.tables.detail(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.managed() })
      void queryClient.invalidateQueries({ queryKey: ['tables', 'admin'] })
    },
  })
}
