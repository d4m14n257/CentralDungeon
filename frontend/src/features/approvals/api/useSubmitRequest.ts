import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { approvalsApi } from './approvalsApi'
import type { SubmitApprovalRequestInput } from '../types'

/**
 * Asks for something (#42).
 *
 * **It notifies nobody.** An admin work item is not duplicated as a notification (#100): the tray is
 * where it appears, and a notice per request would be the copy that decision went to avoid. What
 * does notify is the resolution, and it reaches whoever asked.
 *
 * `meta.showsItsOwnError`: the dialog renders the refusal inline (#197), because the form that was
 * refused is still open and the answer belongs above its button.
 *
 * It invalidates the reader's own list and nothing else: the tray is another audience's cache entry,
 * and whoever is asking has no way to read it.
 *
 * @returns the mutation, taking the kind and the reason
 */
export function useSubmitRequest() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: SubmitApprovalRequestInput) => approvalsApi.submit(input),
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      // The branch and not a patch: what the screen needs back is "is there one pending of this
      // kind", which is an answer about the whole list rather than about the row just written.
      void queryClient.invalidateQueries({ queryKey: queryKeys.requests.mineAll() })
    },
  })
}
