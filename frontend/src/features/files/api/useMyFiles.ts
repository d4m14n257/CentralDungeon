import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { filesApi } from './filesApi'
import type { FileCategory } from '../types'

/**
 * The reuse history of #65 — everything this person uploaded and still keeps, with where each file
 * is being used (#232).
 *
 * This is what the `FilePicker` offers alongside the upload box and what /my/files is built on. It
 * is the cheap half of #75's cost strategy: if finding an old file were harder than dragging in a
 * new one, nobody would reuse.
 *
 * @param query    the search box over their filenames, or undefined for everything
 * @param category the kind of document to narrow to (#233), or undefined for every kind
 * @param page     zero-based page number
 * @param enabled  false to hold the request back until the picker is actually open
 * @returns the query for their files
 */
export function useMyFiles(query?: string, category?: FileCategory, page = 0, enabled = true) {
  return useQuery({
    queryKey: queryKeys.files.mine(query, category, page),
    queryFn: () => filesApi.listMine(query, category, page),
    staleTime: staleTime.files,
    enabled,
  })
}
