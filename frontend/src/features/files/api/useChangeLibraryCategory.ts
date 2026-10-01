import { useMutation, useQueryClient } from '@tanstack/react-query'

import { filesApi } from './filesApi'
import type { LibraryCategoryInput } from '../types'

/**
 * Changing what an unpublished file of the platform's library is (#282). A published one is refused by
 * the server: masters are choosing it under the cajón it has, so it is hidden first.
 *
 * @returns the mutation, taking the file and what it is now
 */
export function useChangeLibraryCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ fileId, input }: { fileId: string; input: LibraryCategoryInput }) => filesApi.changeLibraryCategory(fileId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['files'] })
    },
  })
}
