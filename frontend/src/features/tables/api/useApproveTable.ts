import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'

/**
 * An admin approving a draft - the only road from Preparation to Opened (#27).
 *
 * **Performed from `/admin/queue` and no longer from `/admin/tables`** (#176, F3.3): reviewing is
 * work that waits, so it belongs to the tray. The endpoint did not move — it is the table
 * aggregate's — the screen that offers it did.
 *
 * Approving a table nobody has taken reserves it implicitly; what the backend refuses is approving
 * one a colleague is already working on (`ITEM_ALREADY_CLAIMED`, #100).
 *
 * It invalidates two branches through `queryKeys` and not through a hand-written literal (§3.3): the
 * admin listing, where the row changes status, and **the whole tray**, where the row disappears
 * because the work it represented is done.
 *
 * @returns the mutation, taking the table's id
 */
export function useApproveTable() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (tableId: string) => gameTablesApi.approve(tableId),
    // The tray writes the refusal itself, in the reader's language, from the code (#197). Without
    // this the global toast of `config/query.ts` fires too - and since `ITEM_ALREADY_CLAIMED` is not
    // one of its `EXPLAINED_ERROR_CODES`, the reader would be told "no pudimos completar la acción"
    // on top of (or instead of) the one sentence that says what actually happened.
    meta: { showsItsOwnError: true },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.adminAll() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminQueue.all() })
    },
  })
}
