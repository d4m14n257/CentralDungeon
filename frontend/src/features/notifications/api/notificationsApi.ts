import { api } from '@/api/client'

import type { Notification } from '../types'

/** Always the latest 5 (newest first, already ordered by the backend) - no pagination in E1. */
export const notificationsApi = {
  list: () => api.getPage<Notification>('/api/v1/notifications', { size: 5 }),
  markAsRead: (id: string) => api.patch<void>(`/api/v1/notifications/${id}/read`),
  markAllAsRead: () => api.patch<void>('/api/v1/notifications/read-all'),
}
