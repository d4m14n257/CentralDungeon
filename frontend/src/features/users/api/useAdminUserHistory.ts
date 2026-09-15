import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { adminUsersApi } from './adminUsersApi'

/**
 * What administrators did to one account, and why (#84, #169).
 *
 * **It is the reason the two audit tables are worth writing.** Until F6 brings `audit_logs`, every
 * role change and every block leaves a row with a mandatory justification; without a screen that
 * reads them back, those rows would be write-only and would be born orphaned.
 *
 * Not paginated: it is read as a whole, inside the panel that opens on one person, the same way a
 * table's status history is.
 *
 * **It arrives oldest first**, like a table's status history, and the panel paints it in the order
 * it came: an account's record reads as a sequence — granted, then revoked, then blocked — and a
 * reversed list turns "how did it get here" into a puzzle.
 *
 * @param id      the account whose history to read
 * @param enabled whether to ask at all — the panel is closed most of the time
 * @returns the query for its history, oldest first
 */
export function useAdminUserHistory(id: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.users.adminHistory(id),
    queryFn: () => adminUsersApi.history(id),
    staleTime: staleTime.profile,
    enabled,
  })
}
