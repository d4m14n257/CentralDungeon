import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { pageSize } from '@/config/pagination'
import { staleTime } from '@/config/query'

import { catalogsApi } from './catalogsApi'
import type { CatalogKind, CatalogStatus } from '../types'

/** How the listing is cut. */
export interface AdminCatalogOptions {
  /** One row per group instead of one per value (#275). */
  groupsOnly?: boolean
}

/**
 * The /admin/catalogs table: every value of one catalog, whatever its status, with its group and
 * how much it is used - or, with `groupsOnly`, one row per group.
 *
 * A work list, so it pages by number and keeps the previous page on screen while the next one
 * arrives - without that the table blinks to a skeleton on every click (#173).
 *
 * @param kind     which catalog
 * @param query    the search box, already debounced
 * @param statuses the statuses to keep, or undefined for all of them
 * @param page     zero-based page number
 * @param size     rows per page, one of `adminPageSizeOptions` (#271)
 * @param options  how the listing is cut; by default, every value
 * @returns the query for that page
 */
export function useAdminCatalog(
  kind: CatalogKind,
  query: string,
  statuses: CatalogStatus[] | undefined,
  page: number,
  size: number = pageSize.admin,
  { groupsOnly = false }: AdminCatalogOptions = {},
) {
  return useQuery({
    queryKey: queryKeys.catalogs.admin(kind, query, statuses, page, size, groupsOnly),
    queryFn: () => catalogsApi.adminList(kind, query || undefined, statuses, page, size, groupsOnly),
    staleTime: staleTime.catalogs,
    placeholderData: keepPreviousData,
  })
}
