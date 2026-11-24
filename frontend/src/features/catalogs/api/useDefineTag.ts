import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { catalogsApi } from './catalogsApi'
import type { DefineTagInput } from '../types'

/**
 * Sets a tag group's category and definition. Every catalog read is refreshed afterwards, since the
 * group's aliases show the same definition wherever they appear.
 *
 * @returns the mutation, taking the canonical tag and its new category and definition
 */
export function useDefineTag() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, ...input }: DefineTagInput & { id: string }) => catalogsApi.defineTag(id, input),
    // The dialog that calls this shows the failure under its own form.
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.catalogs.all() })
    },
  })
}
