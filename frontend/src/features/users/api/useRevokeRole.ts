import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { adminUsersApi } from './adminUsersApi'
import type { ChangeUserRoleInput } from '../types'

/**
 * Takes a platform role away.
 *
 * The two refusals that matter are both about the same invariant: an owner cannot revoke their own
 * `Owner` (`CANNOT_REVOKE_OWN_OWNER`) and nobody can revoke the last one (`LAST_OWNER`). A platform
 * with nobody who can hand the rank back has no way to recover from the inside.
 *
 * Revoking a role somebody never had is a no-op the server answers `200` to, so this never needs to
 * guard against it.
 *
 * `meta.showsItsOwnError`: the dialog renders the refusal inline, for the same reason as the grant.
 *
 * @returns the mutation, taking the account and what to take from it
 */
export function useRevokeRole() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ userId, input }: { userId: string; input: ChangeUserRoleInput }) => adminUsersApi.revokeRole(userId, input),
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      // The branch, not a patch of the row: `adminAll()` is a prefix of the detail's own key, so
      // writing the answer in would be undone by this same line (see useGrantRole for the full note).
      void queryClient.invalidateQueries({ queryKey: queryKeys.users.adminAll() })
      // The reader may have just demoted themselves out of a context they are standing in (#222).
      void queryClient.invalidateQueries({ queryKey: queryKeys.users.me() })
    },
  })
}
