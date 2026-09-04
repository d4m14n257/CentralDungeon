import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { filesApi } from './filesApi'

/**
 * The reuse history of #65 — everything this person uploaded and still keeps.
 *
 * This is what the `FilePicker` offers alongside the upload box. It is the cheap half of #75's cost
 * strategy: if finding an old file were harder than dragging in a new one, nobody would reuse.
 *
 * @param query   the search box, or undefined for everything
 * @param page    zero-based page number
 * @param enabled false to hold the request back until the picker is actually open
 * @returns the query for their files
 */
export function useMyFiles(query?: string, page = 0, enabled = true) {
  return useQuery({
    queryKey: queryKeys.files.mine(query, page),
    queryFn: () => filesApi.listMine(query, page),
    staleTime: staleTime.files,
    enabled,
  })
}
