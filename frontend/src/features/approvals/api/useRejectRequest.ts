import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { approvalsApi } from './approvalsApi'
import type { ResolveApprovalRequestInput } from '../types'

/**
 * Rejects a request (#42).
 *
 * **The note is required here too, and this is the act that most needs it**: whoever asked gets a
 * notification saying no, and without the reason it is a refusal they cannot do anything about.
 * Nothing else in the platform changes — a rejection resolves the row and touches no role, no table
 * and no account — so the only two branches it moves are its own and the shared admin tray, where the
 * request stops being work anybody is waiting on (#100, F3.3).
 *
 * Refused with `REQUEST_ALREADY_RESOLVED` when somebody else already resolved it, and since F3.3
 * with `ITEM_ALREADY_CLAIMED` when a colleague has taken it from the shared tray (#100) - never for
 * the absence of a reservation, which this screen has no way to make.
 *
 * @returns the mutation, taking the request and the note
 */
export function useRejectRequest() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ requestId, input }: { requestId: string; input: ResolveApprovalRequestInput }) => approvalsApi.reject(requestId, input),
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.requests.adminAll() })
      // The row leaves the shared tray: resolved work is not work waiting on anybody (#100).
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminQueue.all() })
    },
  })
}
