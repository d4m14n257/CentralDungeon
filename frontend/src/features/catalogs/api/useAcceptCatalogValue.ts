import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { catalogsApi } from './catalogsApi'
import type { CatalogKind } from '../types'

/**
 * Accepts a proposal and classifies it in the same step: a canonical entry of its own, or an alias
 * of a group that already exists (#55).
 *
 * @param kind which catalog
 * @returns the mutation, taking the value and the group to join (null for a group of its own)
 */
export function useAcceptCatalogValue(kind: CatalogKind) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, canonicalId }: { id: string; canonicalId: string | null }) => catalogsApi.accept(kind, id, { canonicalId }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.catalogs.all() })
    },
  })
}
