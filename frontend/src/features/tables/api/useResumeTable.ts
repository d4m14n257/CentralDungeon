import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'

/**
 * Bringing a paused table back.
 *
 * `resume()` had an endpoint and no button on any screen, so a paused table could never be brought back from the application.
 *
 * **It carries no justification**, unlike pausing and cancelling: a reason is asked for when a table
 * stops, and resuming is the return to normal.
 *
 * **And it is the one act on this screen that can genuinely be refused.** While the table was paused
 * its master may have committed to another one, so the backend re-checks the clash before moving
 * anything and answers `409 MASTER_SCHEDULE_CONFLICT` naming the other table — every master is
 * measured, not only the Primary. That is why this mutation
 * shows its own error: the answer worth reading is *which* table it collides with (`resumeErrorKey`),
 * and the global toast would show the generic failure message over the one fact that makes the
 * problem solvable.
 *
 * @returns the mutation, taking the table's id
 */
export function useResumeTable() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (tableId: string) => gameTablesApi.resume(tableId),
    meta: { showsItsOwnError: true },
    onSuccess: (table, tableId) => {
      queryClient.setQueryData(queryKeys.tables.detail(tableId), table)
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.adminAll() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.statusHistory(tableId) })
      // Resuming reschedules from today, keeping the numbering: every calendar that showed the old
      // dates is stale, the players' own week included.
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.list(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.mine(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.schedule.mine() })
    },
  })
}
