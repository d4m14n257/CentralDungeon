import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { live, staleTime } from '@/config/query'

import { adminQueueApi } from './adminQueueApi'

/**
 * The shared admin tray (#100): everything waiting on an admin, oldest first.
 *
 * A working list, so it pages by number and keeps the previous page on screen while the next one
 * arrives: without that, the table blinks to a skeleton on every click (#173).
 *
 * **It refreshes itself, and the numbers come from `config/query.ts`.** This is the one listing whose
 * rows are taken away by somebody else — that is what the reservation is for — so the acceptance of
 * F3.3 asks that a colleague's claim make the row disappear *without a manual reload*. The interval
 * and the focus refetch live in `live.adminQueue` beside the `staleTime` policy rather than here,
 * because how live a screen is is a decision about the data and not a detail of one hook. **F6
 * replaces both with the WebSocket** (#101).
 *
 * @param page zero-based page number
 * @returns the query for that page
 */
export function useAdminQueue(page: number) {
  return useQuery({
    queryKey: queryKeys.adminQueue.list(page),
    queryFn: () => adminQueueApi.list(page),
    staleTime: staleTime.adminQueue,
    placeholderData: keepPreviousData,
    ...live.adminQueue,
  })
}
