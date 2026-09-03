import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { catalogsApi } from './catalogsApi'
import type { CatalogKind } from '../types'

/**
 * One value by id, whatever its status.
 *
 * This is how a table shows the tag its master proposed while it is still pending: the listing
 * endpoint would not return it (#57), and the interface has to be able to say "pending approval"
 * rather than silently drop the tag.
 *
 * @param kind which catalog
 * @param id   the value, or null to skip the query entirely
 * @returns the query for that value
 */
export function useCatalogValue(kind: CatalogKind, id: string | null) {
  return useQuery({
    queryKey: queryKeys.catalogs.detail(kind, id ?? ''),
    queryFn: () => catalogsApi.byId(kind, id as string),
    staleTime: staleTime.catalogs,
    enabled: id !== null,
  })
}
