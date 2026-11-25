import { useMutation, useQueryClient } from '@tanstack/react-query'

import { useClientLimits } from '@/hooks/useClientLimits'
import type { UploadFailure } from '@/types/file'
import { uploadFailureReason } from '../uploadErrors'
import { filesApi } from './filesApi'
import type { FileCategory, StagedFile } from '../types'

/** One file to send into the platform's library, with what it is. */
export interface LibraryUpload {
  /** The file as it was staged. Only `new` entries are sent: the library's dropzone stages nothing else. */
  staged: StagedFile
  /** The category it is published into, chosen on its own row. */
  category: FileCategory
}

/** What sending the staged files into the platform's library came to. */
export interface LibraryUploadResult {
  /** The names that went into the library, in the order they were picked. */
  published: string[]
  /** The files the server could not take, each with why, to be tried again. */
  failed: UploadFailure[]
  /** The names the admin had already uploaded, whose existing row came back published. */
  reused: string[]
}

/**
 * Sends what is staged on /admin/files/upload into the platform's library, each file with the category
 * chosen on its row — published now, or left unpublished for later, as the admin
 * chose with the button they pressed.
 *
 * **A failure does not undo the rest**, the same rule as `useCommitStagedFiles`: each file is sent on
 * its own and the files that failed come back in the result with the translated reason the server gave
 * (or a generic one when there was no code to read), so the screen keeps them listed and the
 * admin can try again — which is free, because the server recognises content it already has.
 *
 * @returns the mutation, taking the files with their categories and whether to publish them now
 */
export function useUploadToLibrary() {
  const queryClient = useQueryClient()
  const { maxFileSizeBytes } = useClientLimits()
  return useMutation({
    // It reports its own failures in the result, so the global toast would be a second, vaguer
    // message about something the screen already names precisely.
    meta: { showsItsOwnError: true },
    mutationFn: async ({ uploads, publish }: { uploads: LibraryUpload[]; publish: boolean }): Promise<LibraryUploadResult> => {
      const published: string[] = []
      const failed: UploadFailure[] = []
      const reused: string[] = []
      for (const { staged, category } of uploads) {
        if (staged.kind !== 'new') continue
        try {
          const uploaded = await filesApi.uploadToLibrary(staged.file, { categories: [category], publish })
          published.push(staged.name)
          if (uploaded.deduplicated) {
            reused.push(staged.name)
          }
        } catch (error) {
          failed.push({ name: staged.name, reason: uploadFailureReason(error, maxFileSizeBytes) })
        }
      }
      return { published, failed, reused }
    },
    onSuccess: () => {
      // The library and every picker that offers what the platform published.
      void queryClient.invalidateQueries({ queryKey: ['files'] })
    },
  })
}
