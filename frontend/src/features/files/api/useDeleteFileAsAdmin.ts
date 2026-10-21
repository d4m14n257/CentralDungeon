import { useMutation, useQueryClient } from '@tanstack/react-query'

import { filesApi } from './filesApi'

/**
 * An admin removing any file, including one somebody else uploaded.
 *
 * Still a mark and never an erase (ADR-03 §7): an admin has more reach than an owner, not a different kind
 * of delete. Freeing the actual bytes is the platform owner's, from `/owner/storage`, and that is F5
 * (ADR-13 §8).
 *
 * @returns the mutation, taking the id of the file to mark gone
 */
export function useDeleteFileAsAdmin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (fileId: string) => filesApi.removeAsAdmin(fileId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['files'] })
    },
  })
}
