import { useMutation, useQueryClient } from '@tanstack/react-query'

import { filesApi } from './filesApi'
import type { FileCategory, StagedFile } from '../types'

/** One file to send into the platform's library, with what it is. */
export interface LibraryUpload {
  /** The file as it was staged. Only `new` entries are sent: the library's dropzone stages nothing else. */
  staged: StagedFile
  /** The cajón it is published into, chosen on its own row (#278). */
  category: FileCategory
}

/** What sending the staged files into the platform's library came to. */
export interface LibraryUploadResult {
  /** The names that went into the library, in the order they were picked. */
  published: string[]
  /** The names the server could not take, to be tried again. */
  failed: string[]
  /** The names the admin had already uploaded, whose existing row came back published (#75, #234). */
  reused: string[]
}

/**
 * Sends what is staged on /admin/files/upload into the platform's library, each file with the cajón
 * chosen on its row (#233, #238, #282) — published now, or left unpublished for later, as the admin
 * chose with the button they pressed.
 *
 * **A failure does not undo the rest**, the same rule as `useCommitStagedFiles`: each file is sent on
 * its own and the names that failed come back in the result, so the screen keeps them listed and the
 * admin can try again — which is free, because the server recognises content it already has.
 *
 * @returns the mutation, taking the files with their cajones and whether to publish them now
 */
export function useUploadToLibrary() {
  const queryClient = useQueryClient()
  return useMutation({
    // It reports its own failures in the result, so the global toast would be a second, vaguer
    // message about something the screen already names precisely.
    meta: { showsItsOwnError: true },
    mutationFn: async ({ uploads, publish }: { uploads: LibraryUpload[]; publish: boolean }): Promise<LibraryUploadResult> => {
      const published: string[] = []
      const failed: string[] = []
      const reused: string[] = []
      for (const { staged, category } of uploads) {
        if (staged.kind !== 'new') continue
        try {
          const uploaded = await filesApi.uploadToLibrary(staged.file, { categories: [category], publish })
          published.push(staged.name)
          if (uploaded.deduplicated) {
            reused.push(staged.name)
          }
        } catch {
          failed.push(staged.name)
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
