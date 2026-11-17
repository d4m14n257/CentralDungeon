import type { QueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

/**
 * Everything lifting a veto makes stale, in one place.
 *
 * **Written once because the list is not obvious** and a copy that falls one entry behind is a
 * cache describing a table the reader may or may not see. A veto changes **who may read the table at
 * all**, and the branches that go stale are branches this feature does not own.
 *
 * - the roster, where the row stops showing as `Blocked`;
 * - the candidate queue, because the person may be back in it;
 * - the table's detail, in the player's, the master's and the admin's flavour, and every listing of
 *   tables, because one reader just got the table back — the explorer filters vetoes in the `WHERE`,
 *   so its cached page is now wrong;
 * - the table's files, which the person can read again.
 *
 * The master's tray goes too: a candidate who is waiting again is work.
 *
 * @param queryClient the client to invalidate on
 * @param tableId     the table the veto was lifted at
 */
export function invalidateAfterVeto(queryClient: QueryClient, tableId: string) {
  void queryClient.invalidateQueries({ queryKey: queryKeys.registrations.players(tableId) })
  void queryClient.invalidateQueries({ queryKey: queryKeys.registrations.candidates(tableId) })
  void queryClient.invalidateQueries({ queryKey: queryKeys.registrations.mine() })
  void queryClient.invalidateQueries({ queryKey: queryKeys.tables.detail(tableId) })
  void queryClient.invalidateQueries({ queryKey: queryKeys.tables.managedDetail(tableId) })
  void queryClient.invalidateQueries({ queryKey: queryKeys.tables.adminAll() })
  void queryClient.invalidateQueries({ queryKey: queryKeys.tables.lists() })
  void queryClient.invalidateQueries({ queryKey: queryKeys.files.table(tableId) })
  void queryClient.invalidateQueries({ queryKey: queryKeys.requests.banRequests(tableId) })
  void queryClient.invalidateQueries({ queryKey: queryKeys.master.dashboard() })
}
