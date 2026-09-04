import { useMutation, useQueryClient } from '@tanstack/react-query'

import { filesApi } from './filesApi'
import type { UploadFileInput } from '../types'

/**
 * Uploading a file.
 *
 * **An upload of content this person already has answers with the file they already had** (#75), so
 * the caller gets a `StoredFile` either way and never has to tell the two apart. That is the point:
 * reuse should be invisible when it happens by itself.
 *
 * @returns the mutation, taking the file and which lifecycle it should have
 */
export function useUploadFile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ file, input }: { file: File; input: UploadFileInput }) => filesApi.upload(file, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['files', 'mine'] })
    },
  })
}
