import { api } from '@/api/client'

import type { Notification } from '../types'

export const notificationsApi = {
  list: () => api.getPage<Notification>('/api/v1/notifications'),
  markAsRead: (id: string) => api.patch<void>(`/api/v1/notifications/${id}/read`),
}
