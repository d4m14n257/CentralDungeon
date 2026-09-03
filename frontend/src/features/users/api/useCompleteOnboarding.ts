import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { usersApi } from './usersApi'

/**
 * Saves the display name and country that onboarding blocks on (#134), and refreshes the profile so
 * the app stops redirecting back to it.
 *
 * @returns the onboarding mutation
 */
export function useCompleteOnboarding() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: usersApi.completeOnboarding,
    onSuccess: (user) => {
      queryClient.setQueryData(queryKeys.users.me(), user)
    },
  })
}
