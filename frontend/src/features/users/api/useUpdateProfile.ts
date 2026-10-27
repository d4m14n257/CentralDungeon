import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { usersApi } from './usersApi'

/**
 * Edits the display name and country once onboarding is done (ADR-10 §4), and refreshes both views of
 * the caller's profile: the shell's `/users/me` and the profile screen's own.
 *
 * Refused with `ONBOARDING_PENDING` while the first step is still pending; that and any other failure
 * reach the person through the global mutation toast.
 *
 * @returns the profile-edit mutation
 */
export function useUpdateProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: usersApi.updateProfile,
    onSuccess: (user) => {
      queryClient.setQueryData(queryKeys.users.me(), user)
      void queryClient.invalidateQueries({ queryKey: queryKeys.profiles.mine() })
    },
  })
}
