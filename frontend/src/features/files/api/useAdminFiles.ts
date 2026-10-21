import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { pageSize } from '@/config/pagination'
import { staleTime } from '@/config/query'

import { filesApi } from './filesApi'
import type { FileCategory } from '../types'

/**
 * /admin/files: the platform's library — only what it published — with each file's owner and usage
 * count (ADR-14 §7).
 *
 * @param query      the search box in the language of ADR-22 §1, or undefined for everything
 * @param statuses   the statuses to keep, or undefined for all of them
 * @param category   the cajón to keep (ADR-14 §4), or undefined for all of them
 * @param page       zero-based page number
 * @param size       rows per page, one of `adminPageSizeOptions` (ADR-21 §11)
 * @returns the query for one page of files
 */
export function useAdminFiles(query?: string, statuses?: string[], category?: FileCategory, page = 0, size: number = pageSize.admin) {
  return useQuery({
    queryKey: queryKeys.files.admin(query, statuses, category, page, size),
    queryFn: () => filesApi.listForAdmin(query, statuses, category, page, size),
    staleTime: staleTime.files,
  })
}
