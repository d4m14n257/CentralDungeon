import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { usersApi } from './usersApi'

export function useCompleteOnboarding() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: usersApi.completeOnboarding,
    onSuccess: (user) => {
      queryClient.setQueryData(queryKeys.users.me(), user)
    },
  })
}
