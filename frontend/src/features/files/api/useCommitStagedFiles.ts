import { useMutation } from '@tanstack/react-query'
import { useQueryClient } from '@tanstack/react-query'

import { useClientLimits } from '@/hooks/useClientLimits'
import type { UploadFailure } from '@/types/file'
import { uploadFailureReason } from '../uploadErrors'
import { filesApi } from './filesApi'
import type { CommitResult, FileCategory, StagedFile } from '../types'

/**
 * Sends everything that was staged, at the moment the operation is confirmed.
 *
 * **This is where uploading happens now, and nowhere else.** A picker stages; the button that creates
 * the table, sends the answer or publishes the request is what puts bytes on the server. Uploading at
 * pick time left a file behind whenever somebody abandoned a wizard halfway.
 *
 * **A failure does not undo the rest.** Each file is sent on its own and the files that failed come
 * back in the result instead of as an exception, each with the translated reason the server gave
 * (the size, the type, too many tries) or a generic one when there was no code to read. The caller
 * has to go on: the table is created, what uploaded is linked, and the person is told what to add
 * and why. Losing four steps of work over one bad file is worse than the missing file, and every
 * flow that uses this can be completed by editing afterwards.
 *
 * **Retrying is free.** The server recognises content somebody already uploaded and hands back the
 * row they had, so sending the list again after fixing one file stores nothing twice.
 *
 * Sequential rather than parallel: the order of the resulting ids is the order they were picked in,
 * and a partial failure is easier to describe when it happened at a known point.
 *
 * @returns the mutation, taking the staged list and the category to file new uploads under
 */
export function useCommitStagedFiles() {
  const queryClient = useQueryClient()
  const { maxFileSizeBytes } = useClientLimits()
  return useMutation({
    // It reports its own failures in the result, so the global toast would be a second, vaguer
    // message about something the screen already names precisely.
    meta: { showsItsOwnError: true },
    mutationFn: async ({
      staged,
      fileCategory = null,
    }: {
      staged: StagedFile[]
      fileCategory?: FileCategory | null
    }): Promise<CommitResult> => {
      const fileIds: string[] = []
      const failed: UploadFailure[] = []
      const reused: string[] = []

      for (const entry of staged) {
        if (entry.kind === 'existing') {
          fileIds.push(entry.fileId)
          continue
        }
        try {
          const uploaded = await filesApi.upload(entry.file, { fileType: 'Private', fileCategory })
          fileIds.push(uploaded.file.id)
          if (uploaded.deduplicated) {
            reused.push(uploaded.file.name)
          }
        } catch (error) {
          failed.push({ name: entry.name, reason: uploadFailureReason(error, maxFileSizeBytes) })
        }
      }
      return { fileIds, failed, reused }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['files', 'mine'] })
    },
  })
}
