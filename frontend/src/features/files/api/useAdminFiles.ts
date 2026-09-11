import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { filesApi } from './filesApi'
import type { FileCategory } from '../types'

/**
 * /admin/files: every file the platform holds, with its owner and its usage count.
 *
 * @param query      the search box in the language of #164, or undefined for everything
 * @param statuses   the statuses to keep, or undefined for all of them
 * @param fileTypes  the lifecycles to keep (#68), or undefined for all of them
 * @param category   the cajón to keep (#233), or undefined for all of them
 * @param page       zero-based page number
 * @returns the query for one page of files
 */
export function useAdminFiles(query?: string, statuses?: string[], fileTypes?: string[], category?: FileCategory, page = 0) {
  return useQuery({
    queryKey: queryKeys.files.admin(query, statuses, fileTypes, category, page),
    queryFn: () => filesApi.listForAdmin(query, statuses, fileTypes, category, page),
    staleTime: staleTime.files,
  })
}
