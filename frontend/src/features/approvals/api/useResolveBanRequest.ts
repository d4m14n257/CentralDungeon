import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { banRequestsApi } from './approvalsApi'
import type { ResolveApprovalRequestInput } from '../types'

interface ResolveBanVariables {
  requestId: string
  input: ResolveApprovalRequestInput
}

/**
 * The `Primary` granting a co-master's veto request (#39).
 *
 * **Approving is what applies the veto**, in one transaction on the backend — there is no second
 * step where somebody then presses "vetar". Which is why this invalidates the registration branches
 * as well as the request list: the person just stopped being able to see the table, and every
 * cached answer that still shows them at it is describing the past.
 *
 * **It never touches `adminQueue`**: a `PlayerBan` is not in the shared tray, by decision. #39 is
 * specific where #90 is generic, and the specific one wins — this is the table's business.
 *
 * The invalidation crosses into another feature's keys and that is allowed: `api/queryKeys.ts` is
 * the shared factory, not a feature (§3.3). What is forbidden is importing another feature's code,
 * which is why the response body — a registration — is not read.
 *
 * @param tableId the table the request belongs to
 * @returns the mutation, taking the request and the note
 */
export function useApproveBanRequest(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ requestId, input }: ResolveBanVariables) => banRequestsApi.approve(tableId, requestId, input),
    /** The dialog is still open over the button: the refusal goes there, not into a toast (#197). */
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.requests.banRequests(tableId) })
      // Granting it applies the veto, so everything a veto makes stale is stale here too (#29).
      void queryClient.invalidateQueries({ queryKey: queryKeys.registrations.players(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.registrations.candidates(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.detail(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.files.table(tableId) })
      // And the master's tray: the request that was waiting is answered, and if the person vetoed was
      // still a candidate, they are no longer somebody waiting to be let in either.
      void queryClient.invalidateQueries({ queryKey: queryKeys.master.dashboard() })
    },
  })
}

/**
 * The `Primary` turning a co-master's veto request down (#39).
 *
 * Nothing about the table moves, so only the request list is re-read. The note is required all the
 * same: it is the whole of what the co-master receives, and a refusal nobody explained is the half
 * that makes the mechanism worthless (#42).
 *
 * @param tableId the table the request belongs to
 * @returns the mutation, taking the request and the note
 */
export function useRejectBanRequest(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ requestId, input }: ResolveBanVariables) => banRequestsApi.reject(tableId, requestId, input),
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.requests.banRequests(tableId) })
    },
  })
}
