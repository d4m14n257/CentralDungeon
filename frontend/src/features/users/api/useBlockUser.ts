import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { adminUsersApi } from './adminUsersApi'
import type { ChangeUserStatusInput } from '../types'

/**
 * Closes an account (#84).
 *
 * **Blocking keeps everything.** The person stops being able to sign in; their tables, applications,
 * sessions and history stay exactly where they were. It is the one thing the dialog has to say out
 * loud before the button is pressed, because "block" is a word people read as "delete".
 *
 * Refused with `CANNOT_BLOCK_PRIVILEGED` on anybody holding `Admin` or `Owner` — between peers there
 * is no authority, and the block is irreversible from the blocked side, who cannot sign in to ask
 * for it to be undone. The screen never offers the button in that case, so the code only surfaces
 * for somebody calling the API another way.
 *
 * `meta.showsItsOwnError`: the dialog renders the refusal inline (#197).
 *
 * @returns the mutation, taking the account and the reason
 */
export function useBlockUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ userId, input }: { userId: string; input: ChangeUserStatusInput }) => adminUsersApi.block(userId, input),
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      // The branch, not a patch of the row: `adminAll()` is a prefix of the detail's own key, so
      // writing the answer in would be undone by this same line (see useGrantRole for the full note).
      void queryClient.invalidateQueries({ queryKey: queryKeys.users.adminAll() })
    },
  })
}
