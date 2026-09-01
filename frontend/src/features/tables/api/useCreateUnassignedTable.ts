import { useMutation, useQueryClient } from '@tanstack/react-query'

import { gameTablesApi } from './gameTablesApi'
import type { CreateGameTableRequest } from '../types'

/** El admin puede crear una mesa sin ser su master (#72); assignMasters es lo que la abre. */
export function useCreateUnassignedTable() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CreateGameTableRequest) => gameTablesApi.createUnassigned(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tables', 'admin'] })
    },
  })
}
