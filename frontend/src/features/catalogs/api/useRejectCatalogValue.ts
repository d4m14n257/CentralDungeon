import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { catalogsApi } from './catalogsApi'
import type { CatalogKind } from '../types'

/**
 * Turns down a proposal: it never shows and never filters (#57).
 *
 * Not the same as disabling. Rejecting says the value should never have been proposed; disabling
 * says it was fine and is no longer in use.
 *
 * @param kind which catalog
 * @returns the mutation, taking the value's id
 */
export function useRejectCatalogValue(kind: CatalogKind) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => catalogsApi.reject(kind, id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.catalogs.all() })
    },
  })
}
