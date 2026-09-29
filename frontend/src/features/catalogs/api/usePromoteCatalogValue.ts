import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { catalogsApi } from './catalogsApi'
import type { CatalogKind } from '../types'

/**
 * Makes an alias the canonical entry of its group (#276). The group keeps every member; only which
 * one heads it changes - so, unlike disabling the head (#183), nothing leaves circulation.
 *
 * @param kind which catalog
 * @returns the mutation, taking the alias to promote
 */
export function usePromoteCatalogValue(kind: CatalogKind) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => catalogsApi.promote(kind, id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.catalogs.all() })
    },
  })
}
