import { useMutation, useQueryClient } from '@tanstack/react-query'

import { filesApi } from './filesApi'

/**
 * Publishing a file of the platform's library that is not published — never published yet, or hidden
 * (#282). From then on masters see it in the picker and can attach it (#79).
 *
 * @returns the mutation, taking the id of the file to publish
 */
export function usePublishFile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (fileId: string) => filesApi.publish(fileId),
    onSuccess: () => {
      // The library and every picker that offers what the platform published.
      void queryClient.invalidateQueries({ queryKey: ['files'] })
    },
  })
}
