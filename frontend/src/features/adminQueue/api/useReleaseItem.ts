import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { adminQueueApi } from './adminQueueApi'
import type { AdminQueueItemType } from '../types'

/**
 * Hands an item back to the tray (#100).
 *
 * **It is the half that makes the reservation bearable.** Without it, an admin who opens something,
 * reads it and decides it is not theirs has locked it for fifteen minutes; with it, the answer to "I
 * am not the right person for this" takes one press instead of a wait. The release job is the safety
 * net for whoever closes the tab, not the normal way out.
 *
 * Invalidates the whole tray branch, like {@link useClaimItem}: a release puts the row back in front
 * of every other admin.
 *
 * @returns the mutation, taking the item's source and its id
 */
export function useReleaseItem() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ type, id }: { type: AdminQueueItemType; id: string }) => adminQueueApi.release(type, id),
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminQueue.all() })
    },
  })
}
