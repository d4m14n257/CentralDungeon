import { useMutation, useQueryClient } from '@tanstack/react-query'

import { filesApi } from './filesApi'

/**
 * Taking a file back out of the platform's published set.
 *
 * Tables that already attached it keep it — unpublishing is not a delete (#79). It returns to its
 * uploader as something they keep rather than as a transient file, so the purge of #75 does not
 * inherit a decision nobody made.
 *
 * @returns the mutation, taking the id of the file to unpublish
 */
export function useUnpublishFile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (fileId: string) => filesApi.unpublish(fileId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['files'] })
    },
  })
}
