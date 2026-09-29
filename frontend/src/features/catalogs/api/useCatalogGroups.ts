import { useQueries, type UseQueryResult } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { catalogsApi } from './catalogsApi'
import type { AdminCatalogValue, CatalogKind } from '../types'

/** Every open group, folded into one answer. */
export interface CatalogGroupsResult {
  /** The groups that have arrived, head first inside each, in the order they were asked for. */
  groups: AdminCatalogValue[][]
  /** Whether any group is still on its first load. */
  isPending: boolean
  /** The first load error among them, or null when none failed. */
  error: Error | null
  /** Reads every group again - what an error state's retry does. */
  refetch: () => void
}

/**
 * Folds the per-group queries into one result. **Module level on purpose**: `useQueries` only
 * memoizes what `combine` returns while the function itself is the same one, and a new array on
 * every render is what sends a canvas that syncs its nodes from this into an update loop.
 *
 * @param results one query result per group
 * @returns the folded result
 */
function combineGroups(results: UseQueryResult<AdminCatalogValue[]>[]): CatalogGroupsResult {
  return {
    groups: results.flatMap((result) => (result.data ? [result.data] : [])),
    isPending: results.some((result) => result.isPending),
    error: results.find((result) => result.isLoadingError)?.error ?? null,
    refetch: () => results.forEach((result) => void result.refetch()),
  }
}

/**
 * Several synonym groups at once - every group the catalog canvas has open (#275).
 *
 * One query per group, under the same key `useCatalogGroup` uses, so a group already read by a
 * dialog is not read again, and an admin mutation (which invalidates the whole catalog branch)
 * refreshes every star on the canvas at once.
 *
 * @param kind which catalog
 * @param ids  any member of each group to read
 * @returns the groups folded into one result, stable while none of them changes
 */
export function useCatalogGroups(kind: CatalogKind, ids: string[]): CatalogGroupsResult {
  return useQueries({
    queries: ids.map((id) => ({
      queryKey: queryKeys.catalogs.group(kind, id),
      queryFn: () => catalogsApi.group(kind, id),
      staleTime: staleTime.catalogs,
    })),
    combine: combineGroups,
  })
}
