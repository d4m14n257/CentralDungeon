import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { filesApi } from './filesApi'
import type { LinkTableFileInput } from '../types'

/** What attaching a batch needs: the table it goes to, and the files. */
export interface AttachFilesInput {
  /** The table, known only once it exists. */
  tableId: string
  /** The attachments, in the order they were picked. */
  files: LinkTableFileInput[]
}

/**
 * Attaches several files to a table whose id the caller did not have when the files were chosen
 * (#228).
 *
 * It exists for the create wizard: a master picks the maps and sheets while building the table, and
 * the table has no id until the moment it is saved. `useAttachTableFile` takes its id at hook time
 * and so cannot be used before that — this one takes it at call time instead.
 *
 * Sequential rather than parallel: a table with three attachments is three rows, and sending them at
 * once buys nothing while making a partial failure harder to describe.
 *
 * @returns the mutation that attaches a batch
 */
export function useAttachFilesToTable() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ tableId, files }: AttachFilesInput) => {
      for (const file of files) {
        await filesApi.attach(tableId, file)
      }
    },
    onSuccess: (_result, { tableId }) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.files.table(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.detail(tableId) })
    },
  })
}
