import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { banRequestsApi } from './approvalsApi'

/**
 * The veto requests waiting on one table (#39, F3.4).
 *
 * **The whole list, never paginated**: it is the pending work of one table, which is a handful of
 * rows at most and usually none. Paging a list that is empty nine times out of ten would be paging
 * for the tenth.
 *
 * Read by every master of the table, `Primary` or not — a co-master who asked has to be able to see
 * that the answer has not come, or they will ask again.
 *
 * **It answers with `BanRequest` and not with the shared approval summary**, which is what lets each
 * line name the person it is about: the summary's entity is whoever asked, so two open requests on
 * one table were told apart only by the wording of their reasons.
 *
 * @param tableId the table
 * @returns the query for its pending veto requests
 */
export function useTableBanRequests(tableId: string) {
  return useQuery({
    queryKey: queryKeys.requests.banRequests(tableId),
    queryFn: () => banRequestsApi.list(tableId),
    staleTime: staleTime.profile,
  })
}
