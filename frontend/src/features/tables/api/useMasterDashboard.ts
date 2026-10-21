import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { masterDashboardApi } from './gameTablesApi'

/**
 * Everything waiting for the reader's answer, across every table they run (ADR-20 §4).
 *
 * No parameter and none possible: the answer is keyed on the token's own actor, so there is no
 * version of this tray about somebody else (ADR-05 §5).
 *
 * @returns the query, with an empty `items` meaning every table is up to date — a success, and the
 *          screen says so in those words
 */
export function useMasterDashboard() {
  return useQuery({
    queryKey: queryKeys.master.dashboard(),
    queryFn: () => masterDashboardApi.get(),
  })
}
