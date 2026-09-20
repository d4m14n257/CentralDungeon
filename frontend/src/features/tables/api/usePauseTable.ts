import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'
import type { ChangeTableStatusRequest } from '../types'

/**
 * An admin pausing a table, with the reason that goes on the record (#32).
 *
 * **One of the three orphans of #163**: `pause()` has had an endpoint since E2 and no button on any
 * screen, which made a whole half of the lifecycle unreachable from the application.
 *
 * Not keyed by table id the way `useCancelTable` is: this one is pressed from a **listing**, where
 * one mutation per row would mean one hook per row. The table travels with the call instead, the
 * same shape `useApproveTable` already uses for the tray.
 *
 * @returns the mutation, taking the table and the justification
 */
export function usePauseTable() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ tableId, request }: { tableId: string; request: ChangeTableStatusRequest }) => gameTablesApi.pause(tableId, request),
    /** The dialog is still open over the button, so the refusal belongs inline and not in a toast (#197). */
    meta: { showsItsOwnError: true },
    onSuccess: (table, { tableId }) => {
      queryClient.setQueryData(queryKeys.tables.detail(tableId), table)
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.adminAll() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.statusHistory(tableId) })
      // A paused table stops promising dates, and the freeze is derived on read - so every calendar
      // that showed its sessions is now answering with something that is no longer true (#32, #33).
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.list(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.schedule.mine() })
      // And the request that asked for it, if a master did, is resolved work.
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminQueue.all() })
    },
  })
}
