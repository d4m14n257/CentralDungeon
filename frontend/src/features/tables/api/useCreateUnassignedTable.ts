import { useMutation, useQueryClient } from '@tanstack/react-query'

import { gameTablesApi } from './gameTablesApi'
import type { CreateGameTableRequest } from '../types'

/** An admin can create a table without running it (#72); assignMasters is what opens it. */
export function useCreateUnassignedTable() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CreateGameTableRequest) => gameTablesApi.createUnassigned(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tables', 'admin'] })
    },
  })
}
