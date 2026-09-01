import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'
import type { CreateGameTableRequest } from '../types'

export function useCreateTable() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CreateGameTableRequest) => gameTablesApi.create(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.managed() })
    },
  })
}
