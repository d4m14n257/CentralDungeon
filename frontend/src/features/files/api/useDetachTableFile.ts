import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { filesApi } from './filesApi'

/**
 * Taking a file off a table.
 *
 * **The file survives** (#79): it stays in its owner's history and on every other table that has it,
 * which is why only this table's branches are invalidated and not the history.
 *
 * @param tableId the table
 * @returns the mutation, taking the id of the file to take off
 */
export function useDetachTableFile(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (fileId: string) => filesApi.detach(tableId, fileId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.files.table(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.detail(tableId) })
    },
  })
}
