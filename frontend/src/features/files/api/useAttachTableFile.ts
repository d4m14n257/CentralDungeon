import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { filesApi } from './filesApi'
import type { LinkTableFileInput } from '../types'

/**
 * Attaching a file to a table — the actor's own, or one the platform published (#79).
 *
 * Two branches are invalidated and both matter: the table's attachments, obviously, and the table's
 * own detail, because a shared attachment is part of what candidates and players read there.
 *
 * @param tableId the table being attached to
 * @returns the mutation, taking the file to attach and how
 */
export function useAttachTableFile(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: LinkTableFileInput) => filesApi.attach(tableId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.files.table(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.detail(tableId) })
      void queryClient.invalidateQueries({ queryKey: ['files', 'mine'] })
    },
  })
}
