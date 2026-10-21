import { useInfiniteQuery, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { live, staleTime } from '@/config/query'

import { notificationsApi } from './notificationsApi'

/**
 * The bell's latest five.
 *
 * staleTime Infinity: in the full design the WebSocket is the only source of change (ADR-18 §2). Until
 * it lands in F6, the bell polls (`live.notifications`) - otherwise a notification that arrives while
 * the page is open never shows until a reload, because an infinitely fresh query is never refetched.
 *
 * @returns the query for the latest notifications
 */
export function useNotifications() {
  return useQuery({
    queryKey: queryKeys.notifications.recent(),
    queryFn: notificationsApi.recent,
    staleTime: staleTime.notifications,
    ...live.notifications,
  })
}

/**
 * The /notifications inbox, one page at a time with "Ver más" (ADR-04 §6): a list to read back through,
 * not one to walk by page number.
 *
 * @returns the infinite query for every notification, newest first
 */
export function useNotificationInbox() {
  return useInfiniteQuery({
    queryKey: queryKeys.notifications.inbox(),
    queryFn: ({ pageParam }) => notificationsApi.list(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined),
    staleTime: staleTime.notifications,
  })
}
