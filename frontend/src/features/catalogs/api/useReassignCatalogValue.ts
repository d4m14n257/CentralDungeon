import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { catalogsApi } from './catalogsApi'
import type { CatalogKind, ReassignCatalogValueInput } from '../types'

/** What moving an alias takes: which alias, and the group it lands in. */
export interface ReassignCatalogValueVariables extends ReassignCatalogValueInput {
  /** The alias that moves. */
  id: string
}

/**
 * Moves an alias from its group to another, in one request (#276) - what dragging it onto another
 * group on the catalog canvas does (#275). Before it existed this was a split and then a merge,
 * with a moment in between where the alias stood alone.
 *
 * Invalidates the whole catalog branch, like every admin operation: two groups changed size.
 *
 * @param kind which catalog
 * @returns the mutation, taking the alias and the group it lands in
 */
export function useReassignCatalogValue(kind: CatalogKind) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, canonicalId }: ReassignCatalogValueVariables) => catalogsApi.reassign(kind, id, { canonicalId }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.catalogs.all() })
    },
  })
}
