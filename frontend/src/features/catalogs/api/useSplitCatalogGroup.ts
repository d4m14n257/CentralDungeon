import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { catalogsApi } from './catalogsApi'
import type { CatalogKind } from '../types'

/**
 * Takes one alias out of its group; it becomes a canonical entry of its own. The undo of a merge
 * that turned out wrong (#55).
 *
 * @param kind which catalog
 * @returns the mutation, taking the alias that leaves
 */
export function useSplitCatalogGroup(kind: CatalogKind) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (memberId: string) => catalogsApi.split(kind, { memberId }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.catalogs.all() })
    },
  })
}
