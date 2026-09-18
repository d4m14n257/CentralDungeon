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
 * and no account, which is why this invalidates only its own branch.
 *
 * Refused with `REQUEST_ALREADY_RESOLVED` when somebody else already resolved it.
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
    },
  })
}
