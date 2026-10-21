import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { catalogsApi } from './catalogsApi'
import type { CatalogKind } from '../types'

/**
 * Takes a value out of circulation (ADR-12 §5). Logical, never physical: every link that points at it
 * keeps its row, so restoring puts everything back with no migration.
 *
 * Disabling a canonical entry that still has live aliases needs a successor, because under a flat
 * `canonical_id` that *is* changing the group's canonical (ADR-12 §2) - and the admin picks who takes
 * over, never an arbitrary first alias (ADR-12 §3).
 *
 * @param kind which catalog
 * @returns the mutation, taking the value and, when it applies, its successor
 */
export function useDisableCatalogValue(kind: CatalogKind) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, newCanonicalId }: { id: string; newCanonicalId: string | null }) =>
      catalogsApi.disable(kind, id, { newCanonicalId }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.catalogs.all() })
    },
  })
}
