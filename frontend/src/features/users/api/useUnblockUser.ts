import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { adminUsersApi } from './adminUsersApi'
import type { ChangeUserStatusInput } from '../types'

/**
 * Reopens a blocked account, with the reason recorded next to the block it undoes.
 *
 * It only applies to `Blocked`: an account that is `Allowed` answers `USER_NOT_BLOCKED`, and a
 * `Deleted` one is not brought back here either — F3.1 does not touch that status in either
 * direction.
 *
 * `meta.showsItsOwnError`: the dialog renders the refusal inline (#197).
 *
 * @returns the mutation, taking the account and the reason
 */
export function useUnblockUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ userId, input }: { userId: string; input: ChangeUserStatusInput }) => adminUsersApi.unblock(userId, input),
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      // The branch, not a patch of the row: `adminAll()` is a prefix of the detail's own key, so
      // writing the answer in would be undone by this same line (see useGrantRole for the full note).
      void queryClient.invalidateQueries({ queryKey: queryKeys.users.adminAll() })
    },
  })
}
