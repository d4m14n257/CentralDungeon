import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { catalogsApi } from './catalogsApi'
import type { CatalogKind } from '../types'

/**
 * Puts a disabled value back in circulation, in the group it was in (#81).
 *
 * @param kind which catalog
 * @returns the mutation, taking the value's id
 */
export function useRestoreCatalogValue(kind: CatalogKind) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => catalogsApi.restore(kind, id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.catalogs.all() })
    },
  })
}
