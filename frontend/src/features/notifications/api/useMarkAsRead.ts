import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { notificationsApi } from './notificationsApi'

/**
 * Marks one notification as seen and invalidates the inbox, which is what drops the bell's count.
 *
 * @returns the mutation, taking the notification's id
 */
export function useMarkAsRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: notificationsApi.markAsRead,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.notifications.list() })
    },
  })
}
