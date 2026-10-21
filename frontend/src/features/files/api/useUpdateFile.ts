import { useMutation, useQueryClient } from '@tanstack/react-query'

import { filesApi } from './filesApi'
import type { UpdateFileInput } from '../types'

/**
 * Renaming a file, and deciding whether to keep it in the reuse history (ADR-13 §2).
 *
 * Invalidates the whole `files` branch: keeping a file changes whether it appears in the history,
 * and renaming it changes every table's row for it — one file is shown in more places than one
 * cache entry covers (ADR-14 §1).
 *
 * @returns the mutation, taking the file id and the state it should end in
 */
export function useUpdateFile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ fileId, input }: { fileId: string; input: UpdateFileInput }) => filesApi.update(fileId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['files'] })
    },
  })
}
