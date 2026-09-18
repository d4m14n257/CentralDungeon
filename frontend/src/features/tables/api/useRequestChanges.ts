import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'
import type { ChangeTableStatusRequest } from '../types'

/**
 * Sends a draft back to its master with a reason. The reason is required and is what the master
 * reads on the status tab.
 *
 * **Performed from `/admin/queue` and no longer from `/admin/tables`** (#176, F3.3) — the same move
 * as `useApproveTable`, and for the same reason: it is the other half of one decision. Refused only
 * when a colleague already holds the table (`ITEM_ALREADY_CLAIMED`, #100).
 *
 * Invalidates the admin listing and the whole tray through `queryKeys`: the table leaves
 * `Preparation`, which is the only status the tray draws tables from.
 *
 * @returns the mutation, taking the table's id and the justification
 */
export function useRequestChanges() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ tableId, request }: { tableId: string; request: ChangeTableStatusRequest }) =>
      gameTablesApi.requestChanges(tableId, request),
    // The dialog writes the refusal itself, over the button that was pressed (#197). Without this
    // the global toast of `config/query.ts` fires as well, so the reader gets two messages for one
    // failure - the good sentence inline and "no pudimos completar la acción" on top of it.
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.adminAll() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminQueue.all() })
    },
  })
}
