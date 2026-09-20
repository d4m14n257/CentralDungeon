import type { QueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

/**
 * Everything a veto makes stale, in one place (F3.4).
 *
 * **Written once rather than copied into the three mutations**, because the list is not obvious and
 * a copy that falls one entry behind is a cache describing a table the reader can no longer see. A
 * veto is the widest-reaching write the master's screens have: it does not change a row, it changes
 * **who may read the table at all** (#29), and the branches that go stale are branches this feature
 * does not own.
 *
 * - the roster, which is where the row now shows as `Blocked`;
 * - the candidate queue, because the person can no longer be in it;
 * - the table's detail and every listing of tables, because one reader just stopped being allowed
 *   to see it — the explorer filters vetoes in the `WHERE`, so its cached page is now wrong;
 * - the table's files, which is the read path #206 anticipated: what a table shares is read by
 *   whoever can see the table, and the vetoed person no longer can.
 *
 * The master's tray goes too: a candidate who is no longer waiting is no longer work.
 *
 * **And the table's pending veto requests**, which is the one nobody would think of: a `Secondary`
 * asks for X to be vetoed, the `Primary` vetoes X directly with the row's own button instead of
 * answering the request, and the backend resolves that now-moot request as part of the same act. A
 * frontend that did not re-read the list would leave it sitting there `Pending` — an answer the
 * `Primary` can only give to something that has already happened.
 *
 * @param queryClient the client to invalidate on
 * @param tableId     the table the veto happened at
 */
export function invalidateAfterVeto(queryClient: QueryClient, tableId: string) {
  void queryClient.invalidateQueries({ queryKey: queryKeys.registrations.players(tableId) })
  void queryClient.invalidateQueries({ queryKey: queryKeys.registrations.candidates(tableId) })
  void queryClient.invalidateQueries({ queryKey: queryKeys.registrations.mine() })
  void queryClient.invalidateQueries({ queryKey: queryKeys.tables.detail(tableId) })
  void queryClient.invalidateQueries({ queryKey: queryKeys.tables.lists() })
  void queryClient.invalidateQueries({ queryKey: queryKeys.files.table(tableId) })
  void queryClient.invalidateQueries({ queryKey: queryKeys.requests.banRequests(tableId) })
  void queryClient.invalidateQueries({ queryKey: queryKeys.master.dashboard() })
}
