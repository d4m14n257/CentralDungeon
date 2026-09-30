import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { catalogsApi } from './catalogsApi'
import type { CatalogKind, ProposeCatalogValueInput } from '../types'

/**
 * An admin adding a value, born `Accepted` as a group of its own (#280). Grouping it with others
 * afterwards is the canvas's job (#275), like for any other value.
 *
 * @param kind which catalog
 * @returns the mutation, taking the name to add
 */
export function useCreateCatalogValue(kind: CatalogKind) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: ProposeCatalogValueInput) => catalogsApi.create(kind, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.catalogs.all() })
    },
  })
}
