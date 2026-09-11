import { useMutation, useQueryClient } from '@tanstack/react-query'

import { filesApi } from './filesApi'
import type { UploadFileInput } from '../types'

/**
 * Uploading a file.
 *
 * **An upload of content this person already has answers with the file they already had** (#75), so
 * the caller gets the file either way and never has to tell the two apart to keep working. What it
 * *can* do is say so: `deduplicated` rides alongside (#234), because reuse that happens in complete
 * silence teaches nobody that it happened.
 *
 * `meta.showsItsOwnError` opts out of the global failure toast — `FileDropzone` puts the message
 * under the drop area instead, where the person is already looking and where it stays while they
 * pick another file. Any caller that does **not** show its own error must leave this off.
 *
 * @returns the mutation, taking the file, its lifecycle (#68) and its category (#233)
 */
export function useUploadFile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ file, input }: { file: File; input: UploadFileInput }) => filesApi.upload(file, input),
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['files', 'mine'] })
    },
  })
}
