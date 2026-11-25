import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'

import { queryKeys } from '@/api/queryKeys'
import { live, staleTime } from '@/config/query'

import { notificationsApi } from './notificationsApi'

/**
 * The bell's latest five.
 *
 * staleTime Infinity: in the full design the WebSocket is the only source of change. Until
 * it lands, the bell polls (`live.notifications`) - otherwise a notification that arrives while
 * the page is open never shows until a reload, because an infinitely fresh query is never refetched.
 *
 * **An approved request may have changed what the shell offers**: a granted role, or a draft the
 * reader now runs, adds the master context. So when an unread approval shows up, the signed-in
 * profile is refetched, and the context appears without a reload.
 *
 * @returns the query for the latest notifications
 */
export function useNotifications() {
  const queryClient = useQueryClient()
  const handled = useRef(new Set<string>())
  const query = useQuery({
    queryKey: queryKeys.notifications.recent(),
    queryFn: notificationsApi.recent,
    staleTime: staleTime.notifications,
    ...live.notifications,
  })

  useEffect(() => {
    const arrived = (query.data?.content ?? []).filter(
      (notification) =>
        notification.notificationType === 'ApprovalRequestApproved' &&
        notification.readStatus === 'Unread' &&
        !handled.current.has(notification.id),
    )
    if (arrived.length === 0) return
    arrived.forEach((notification) => handled.current.add(notification.id))
    void queryClient.invalidateQueries({ queryKey: queryKeys.users.me() })
  }, [query.data, queryClient])

  return query
}

/**
 * The /notifications inbox, one page at a time with a "show more" button: a list to read back through,
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
