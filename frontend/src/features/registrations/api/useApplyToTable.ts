import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import type { CreateRegistrationInput } from '../types'
import { registrationsApi } from './registrationsApi'

export function useApplyToTable(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateRegistrationInput) => registrationsApi.apply(tableId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.detail(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.registrations.mine() })
    },
  })
}
