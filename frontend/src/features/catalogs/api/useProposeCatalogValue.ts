import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { catalogsApi } from './catalogsApi'
import type { CatalogKind } from '../types'

/**
 * Proposes a value from the wizard's combobox (ADR-12 §3).
 *
 * The value comes back in `Created`, which is why the combobox has to keep showing it and mark it
 * pending: it will not appear in the accepted list until an admin reviews it (ADR-12 §3).
 *
 * @param kind which catalog to add to
 * @returns the mutation, taking the name to propose
 */
export function useProposeCatalogValue(kind: CatalogKind) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (name: string) => catalogsApi.propose(kind, { name }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.catalogs.all() })
    },
  })
}
