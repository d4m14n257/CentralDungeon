import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { pageSize } from '@/config/pagination'
import { live, staleTime } from '@/config/query'

import { queueApi } from './queueApi'

/**
 * The shared admin tray: everything waiting on an admin, oldest first.
 *
 * A working list, so it pages by number and keeps the previous page on screen while the next one
 * arrives: without that, the table blinks to a skeleton on every click.
 *
 * **It refreshes itself, and the numbers come from `config/query.ts`.** This is the one listing whose
 * rows are taken away by somebody else — that is what the reservation is for — so a colleague's
 * claim has to make the row disappear *without a manual reload*. The interval
 * and the focus refetch live in `live.queue` beside the `staleTime` policy rather than here,
 * because how live a screen is is a decision about the data and not a detail of one hook. **A
 * WebSocket will replace both**.
 *
 * @param page zero-based page number
 * @param size rows per page, one of `adminPageSizeOptions`
 * @returns the query for that page
 */
export function useQueue(page: number, size: number = pageSize.admin) {
  return useQuery({
    queryKey: queryKeys.queue.list(page, size),
    queryFn: () => queueApi.list(page, size),
    staleTime: staleTime.queue,
    placeholderData: keepPreviousData,
    ...live.queue,
  })
}
