import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { adminQueueApi } from './adminQueueApi'
import type { AdminQueueItemType } from '../types'

/**
 * Takes an item of the tray for the reader (#100).
 *
 * **Reserving is not a formality**: resolving anything requires holding it, so this is the first half
 * of every action on the tray. What it buys is that the same request is not approved twice by two
 * people who both had it on screen — the exact failure the shared tray exists to prevent.
 *
 * It invalidates the **whole** tray branch and not the row: a claim takes the item out of every other
 * admin's listing and shifts every page after it, so patching one entry would leave the rest of the
 * cache describing a state that no longer is.
 *
 * `meta.showsItsOwnError` because the refusal that matters — a colleague got there first — belongs on
 * the row that was just pressed and in the reader's language, not in the generic toast.
 *
 * @returns the mutation, taking the item's source and its id
 */
export function useClaimItem() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ type, id }: { type: AdminQueueItemType; id: string }) => adminQueueApi.claim(type, id),
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminQueue.all() })
    },
  })
}
