import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { approvalsApi } from './approvalsApi'

/**
 * One request in full, as an admin reads it.
 *
 * **It exists for the half the listing does not carry**: who resolved it, when, and the note they
 * wrote. A tray filtered by `Approved` or `Rejected` shows rows whose whole point is the reason
 * behind them, and the summary has no field for it.
 *
 * @param id the request to read, or null while no panel is open
 * @returns the query, idle until there is an id
 */
export function useAdminRequest(id: string | null) {
  return useQuery({
    queryKey: queryKeys.requests.adminDetail(id ?? ''),
    queryFn: () => approvalsApi.byId(id as string),
    enabled: id !== null,
    staleTime: staleTime.profile,
  })
}
