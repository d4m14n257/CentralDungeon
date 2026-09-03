import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { catalogsApi } from './catalogsApi'
import type { CatalogKind } from '../types'

/**
 * The accepted values a combobox offers, narrowed by whatever the person typed.
 *
 * `staleTime` is an hour: only an admin changes a catalog, from /admin/catalogs, so re-asking on
 * every mount would be pure noise (arquitectura.md 3.3).
 *
 * @param kind  which catalog to read
 * @param query the search box; the caller debounces it before it gets here
 * @returns the query for the matching accepted values
 */
export function useCatalogValues(kind: CatalogKind, query: string) {
  return useQuery({
    queryKey: queryKeys.catalogs.list(kind, query),
    queryFn: () => catalogsApi.list(kind, query || undefined),
    staleTime: staleTime.catalogs,
  })
}
