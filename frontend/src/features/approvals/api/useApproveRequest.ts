import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { approvalsApi } from './approvalsApi'
import type { ResolveApprovalRequestInput } from '../types'

/**
 * Approves a request, with the note that is required on both acts (#42).
 *
 * **Approving a `MasterGrant` grants the role through `UserRoleService`** — the same single road
 * `/admin/users` uses, and not a second one (fase-3-admin-owner.md:128). That is why this
 * invalidates the users branch as well: the role it just moved is on a screen this one knows nothing
 * about, and leaving that cache in place would show an account without a role it now holds.
 *
 * **Approving a `TableOpen` does not create the table.** The request carries no name, no system, no
 * seats and no agenda, so there is nothing to create it from; what approving records is that the
 * request stands, and the table is created by an admin from `/admin/tables` (#72). `General` has no
 * effect beyond being closed — that is its nature.
 *
 * Refused with `REQUEST_ALREADY_RESOLVED` when somebody else got there first, and with
 * `REQUEST_ENTITY_GONE` when what the request pointed at no longer exists (#78). The first is the
 * race a screen cannot prevent on its own, which is why the dialog shows the refusal inline.
 *
 * @returns the mutation, taking the request and the note
 */
export function useApproveRequest() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ requestId, input }: { requestId: string; input: ResolveApprovalRequestInput }) => approvalsApi.approve(requestId, input),
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.requests.adminAll() })
      // A granted role lands on an account, and that account is listed somewhere else entirely.
      void queryClient.invalidateQueries({ queryKey: queryKeys.users.adminAll() })
    },
  })
}
