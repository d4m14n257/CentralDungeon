import { useMutation, useQueryClient } from '@tanstack/react-query'

import { filesApi } from './filesApi'
import type { PublishFileInput } from '../types'

/**
 * An admin publishing a file for the whole platform, with its audience (#64).
 *
 * This is what makes #79 work: once the community's default character sheet is published, masters
 * attach *that* file instead of uploading their own copy.
 *
 * @returns the mutation, taking the file id and who it is for
 */
export function usePublishFile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ fileId, input }: { fileId: string; input: PublishFileInput }) => filesApi.publish(fileId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['files'] })
    },
  })
}
