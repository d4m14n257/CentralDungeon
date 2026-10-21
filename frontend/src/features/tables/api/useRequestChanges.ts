import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'
import type { ChangeTableStatusRequest } from '../types'

/**
 * Sends a draft back to its master with a reason. The reason is required and is what the master
 * reads on the history tab.
 *
 * **Performed from `/admin/queue` and no longer from `/admin/tables`** (ADR-16 §7, F3.3) — the same move
 * as `useApproveTable`, and for the same reason: it is the other half of one decision. Refused only
 * when a colleague already holds the table (`ITEM_ALREADY_CLAIMED`, ADR-16 §5).
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
    // The dialog writes the refusal itself, over the button that was pressed (ADR-23 §3). Without this
    // the global toast of `config/query.ts` fires as well, so the reader gets two messages for one
    // failure - the good sentence inline and "no pudimos completar la acción" on top of it.
    meta: { showsItsOwnError: true },
    onSuccess: (_table, { tableId }) => {
      // The table's own admin view too (ADR-06 §8): approving or sending it back from its status tab has to
      // show the new status and the new line of history without a reload.
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.managedDetail(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.statusHistory(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.adminAll() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminQueue.all() })
    },
  })
}
