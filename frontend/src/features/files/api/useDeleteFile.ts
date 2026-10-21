import { useMutation, useQueryClient } from '@tanstack/react-query'

import { filesApi } from './filesApi'

/**
 * The owner letting go of their own file. Marks it gone; the bytes wait for F5 (ADR-03 §7, ADR-13 §8).
 *
 * Invalidates the whole `files` branch, because a deleted file also stops showing on every table
 * that had it attached — the link survives as a record, the file does not (ADR-14 §1).
 *
 * @returns the mutation, taking the id of the file to let go of
 */
export function useDeleteFile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (fileId: string) => filesApi.remove(fileId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['files'] })
    },
  })
}
