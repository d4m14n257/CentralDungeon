import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { catalogsApi } from './catalogsApi'
import type { CatalogKind } from '../types'

/**
 * One value's whole synonym group, canonical entry first.
 *
 * It is what lets /admin/catalogs answer two things a single row cannot: which synonyms a value
 * has, and - when the canonical entry is being disabled - who can take the group over (#59). Every
 * member comes back, whatever its status, because a disabled synonym is still something to restore.
 *
 * @param kind which catalog
 * @param id   any member of the group, or null to skip the query
 * @returns the query for that group
 */
export function useCatalogGroup(kind: CatalogKind, id: string | null) {
  return useQuery({
    queryKey: queryKeys.catalogs.group(kind, id ?? ''),
    queryFn: () => catalogsApi.group(kind, id as string),
    staleTime: staleTime.catalogs,
    enabled: id !== null,
  })
}
