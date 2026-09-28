import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { pageSize } from '@/config/pagination'
import { staleTime } from '@/config/query'

import { catalogsApi } from './catalogsApi'
import type { CatalogKind, CatalogStatus } from '../types'

/**
 * The /admin/catalogs table: every value of one catalog, whatever its status, with its group and
 * how much it is used.
 *
 * A work list, so it pages by number and keeps the previous page on screen while the next one
 * arrives - without that the table blinks to a skeleton on every click (#173).
 *
 * @param kind     which catalog
 * @param query    the search box, already debounced
 * @param statuses the statuses to keep, or undefined for all of them
 * @param page     zero-based page number
 * @param size     rows per page, one of `adminPageSizeOptions` (#271)
 * @returns the query for that page
 */
export function useAdminCatalog(
  kind: CatalogKind,
  query: string,
  statuses: CatalogStatus[] | undefined,
  page: number,
  size: number = pageSize.admin,
) {
  return useQuery({
    queryKey: queryKeys.catalogs.admin(kind, query, statuses, page, size),
    queryFn: () => catalogsApi.adminList(kind, query || undefined, statuses, page, size),
    staleTime: staleTime.catalogs,
    placeholderData: keepPreviousData,
  })
}
