import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { adminUsersApi } from './adminUsersApi'
import type { ChangeUserRoleInput } from '../types'

/**
 * Gives somebody a platform role (ADR-06 §1).
 *
 * Granting `Admin` to an `Owner` removes `Owner` (nobody grants `Owner` from the application), in the same transaction and
 * on the server — except to oneself, which is refused as `CANNOT_REVOKE_OWN_OWNER` — so one press can move a role this screen never asked about, and the listing has no
 * way to know which rows that touched. That is why the whole admin branch is re-read rather than the
 * pressed row being patched with what came back.
 *
 * `meta.showsItsOwnError` opts out of the global failure toast: the refusals this call has are
 * specific and worth reading — an admin reaching for a rank they cannot hand out, or a grant that
 * would leave the platform without an owner or take away the actor's own — and the dialog puts the sentence where the person is
 * already looking, right above the button they pressed (ADR-23 §3).
 *
 * @returns the mutation, taking the account and what to grant it
 */
export function useGrantRole() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ userId, input }: { userId: string; input: ChangeUserRoleInput }) => adminUsersApi.grantRole(userId, input),
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      // The whole admin branch, and **not** a patch of the row with the detail that came back:
      // `adminAll()` is `['users','admin']`, which is a prefix of the detail's own key, so writing
      // the answer in would only be marked stale by the very next line. One grant can also move a
      // role this screen did not ask about - granting `Admin` revokes `Owner` in the same
      // transaction (ADR-06 §1) - so the branch is what has to be re-read anyway.
      void queryClient.invalidateQueries({ queryKey: queryKeys.users.adminAll() })
      // Whoever was promoted may be the person reading the screen: the shell decides which contexts
      // to offer from `me.roles`, and leaving it stale would hide a context they just gained (ADR-20 §2).
      void queryClient.invalidateQueries({ queryKey: queryKeys.users.me() })
    },
  })
}
