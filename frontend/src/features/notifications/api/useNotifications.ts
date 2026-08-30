import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { notificationsApi } from './notificationsApi'

/** staleTime Infinity: the WebSocket is the only source of change in the full design (#116); until E6 this just means we refetch on remount. */
export function useNotifications() {
  return useQuery({
    queryKey: queryKeys.notifications.list(),
    queryFn: notificationsApi.list,
    staleTime: staleTime.notifications,
  })
}
