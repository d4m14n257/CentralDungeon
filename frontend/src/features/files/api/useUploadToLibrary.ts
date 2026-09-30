import { useMutation, useQueryClient } from '@tanstack/react-query'

import { filesApi } from './filesApi'
import type { FileCategory, StagedFile } from '../types'

/** What sending the staged files into the platform's library came to. */
export interface LibraryUploadResult {
  /** The names that were published, in the order they were picked. */
  published: string[]
  /** The names the server could not take, to be tried again. */
  failed: string[]
  /** The names the admin had already uploaded, whose existing row came back published (#75, #234). */
  reused: string[]
}

/**
 * Sends what is staged on /admin/files into the platform's library, published into the cajones the
 * admin chose beforehand (#233, #238, #278).
 *
 * **Uploading is publishing here**, so every file goes up with the same cajones and comes back
 * `Public` — there is no step in between where it sits in the library saying nothing.
 *
 * **A failure does not undo the rest**, the same rule as `useCommitStagedFiles`: each file is sent
 * on its own and the names that failed come back in the result, so the screen can keep them listed
 * and let the admin try again — which is free, because the server recognises content it already has.
 *
 * Only `new` entries are sent: the dropzone of the library stages nothing else, and a file that is
 * already somebody's is not something an admin publishes from here (#278).
 *
 * @returns the mutation, taking the staged list and the cajones to publish into
 */
export function useUploadToLibrary() {
  const queryClient = useQueryClient()
  return useMutation({
    // It reports its own failures in the result, so the global toast would be a second, vaguer
    // message about something the screen already names precisely.
    meta: { showsItsOwnError: true },
    mutationFn: async ({ staged, categories }: { staged: StagedFile[]; categories: FileCategory[] }): Promise<LibraryUploadResult> => {
      const published: string[] = []
      const failed: string[] = []
      const reused: string[] = []
      for (const entry of staged) {
        if (entry.kind !== 'new') continue
        try {
          const uploaded = await filesApi.uploadToLibrary(entry.file, { categories })
          published.push(entry.name)
          if (uploaded.deduplicated) {
            reused.push(entry.name)
          }
        } catch {
          failed.push(entry.name)
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
