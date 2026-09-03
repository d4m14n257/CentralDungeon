import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { catalogsApi } from './catalogsApi'
import type { CatalogKind, MergeCatalogGroupsInput } from '../types'

/**
 * Merges two synonym groups into one (#55).
 *
 * Not one row of the bridge tables moves, and that is the point of #56: a table tagged "DANDD"
 * becomes findable by "D&D 5e" the moment this runs, with nothing migrated.
 *
 * @param kind which catalog
 * @returns the mutation, taking the group that stops being one and the one that survives
 */
export function useMergeCatalogGroups(kind: CatalogKind) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: MergeCatalogGroupsInput) => catalogsApi.merge(kind, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.catalogs.all() })
    },
  })
}
