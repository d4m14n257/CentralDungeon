import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'
import type { ChangeTableStatusRequest } from '../types'

/**
 * The master **asking** for a pause (#32, F3.4).
 *
 * **Asking is not pausing, and the screen has to keep the two apart.** The table moves to
 * `PauseRequested` and a `TablePause` request lands in the admin tray; the agenda keeps promising
 * dates until an admin answers, because a pause that a master could declare on their own would let
 * anybody freeze the sessions their players are already committed to.
 *
 * `PauseRequested` was a status nothing produced — one of the three orphans F1.7 surveyed and could
 * not close (#163). This is its producer.
 *
 * **It invalidates the shared tray too**: the request it just created is work waiting on an admin,
 * and the master is not the only person looking at it.
 *
 * @param tableId the table whose pause is being asked for
 * @returns the mutation, taking the justification
 */
export function useRequestTablePause(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: ChangeTableStatusRequest) => gameTablesApi.requestPause(tableId, request),
    /**
     * The dialog is still open over the button, so the refusal belongs there and not in a toast
     * (#197). `PAUSE_ALREADY_REQUESTED` is the one worth the sentence: it means somebody else
     * already asked, which the reader resolves by reloading rather than by pressing again.
     */
    meta: { showsItsOwnError: true },
    onSuccess: (table) => {
      queryClient.setQueryData(queryKeys.tables.managedDetail(tableId), table)
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.statusHistory(tableId) })
      // The table changed status, so every listing that shows one is now describing the past.
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.adminAll() })
      // And a new request is waiting on somebody: it belongs in the shared tray (#100).
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminQueue.all() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.requests.adminAll() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.requests.mineAll() })
    },
  })
}
