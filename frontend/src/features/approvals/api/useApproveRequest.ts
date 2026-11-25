import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { approvalsApi } from './approvalsApi'
import type { ResolveApprovalRequestInput } from '../types'

/**
 * Approves a request, with the note that is required on both acts.
 *
 * **Approving a `MasterGrant` grants the role through `UserRoleService`** — the same single road
 * `/admin/users` uses, and not a second one. That is why this
 * invalidates the users branch as well: the role it just moved is on a screen this one knows nothing
 * about, and leaving that cache in place would show an account without a role it now holds.
 *
 * **Approving a `TableOpen` creates a draft** with the name the requester proposed, run by them as
 * its Primary and without granting them the role; they fill it in and send it to review themselves.
 * `General` has no effect beyond being closed — that is its nature.
 *
 * Refused with `REQUEST_ALREADY_RESOLVED` when somebody else got there first, with
 * `REQUEST_ENTITY_GONE` when what the request pointed at no longer exists, and
 * with `ITEM_ALREADY_CLAIMED` when a colleague has taken it from the shared tray. **No reservation
 * is required to approve**: `/admin/requests` offers no way to make one, and resolving something
 * nobody holds takes it implicitly. All three are races a screen cannot prevent on its own, which is
 * why the dialog shows the refusal inline.
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
      // And the row leaves the shared tray: resolved work is not work waiting on anybody.
      void queryClient.invalidateQueries({ queryKey: queryKeys.queue.all() })
      // Approving a veto request is the veto: the table's lists of requests, people and tables go stale.
      void queryClient.invalidateQueries({ queryKey: queryKeys.requests.banAll() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.registrations.all() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.details() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.managedDetails() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.adminAll() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.master.dashboard() })
    },
  })
}
