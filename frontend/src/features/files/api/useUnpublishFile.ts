import { useMutation, useQueryClient } from '@tanstack/react-query'

import { filesApi } from './filesApi'

/**
 * Hiding a published file: it stays in the platform's library, offered to nobody, and can be published
 * again (#282).
 *
 * Tables that already attached it keep it — hiding is not a delete (#79).
 *
 * @returns the mutation, taking the id of the file to hide
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
