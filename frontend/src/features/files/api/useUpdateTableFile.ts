import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { filesApi } from './filesApi'
import type { UpdateTableFileInput } from '../types'

/**
 * Changing what an attachment is for, or whether the table's players see it.
 *
 * Nothing here reaches the file: sharing a map on this table says nothing about the same map on
 * another one, which is exactly what `table_files` exists for (#79).
 *
 * @param tableId the table
 * @returns the mutation, taking the file id and what the attachment should become
 */
export function useUpdateTableFile(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ fileId, input }: { fileId: string; input: UpdateTableFileInput }) => filesApi.updateAttachment(tableId, fileId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.files.table(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.detail(tableId) })
    },
  })
}
