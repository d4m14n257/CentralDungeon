import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'
import type { CreateGameTableRequest } from '../types'

/**
 * Creates a table. The creator becomes its master, which is what makes it theirs (#73, #135).
 *
 * @returns the mutation, taking the draft
 */
export function useCreateTable() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CreateGameTableRequest) => gameTablesApi.create(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.managed() })
    },
  })
}
