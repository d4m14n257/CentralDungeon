import { api } from '@/api/client'

import type { Notification } from '../types'

/** Siempre las últimas 5 (más recientes primero, ya ordenadas por el backend) - sin paginación en E1. */
export const notificationsApi = {
  list: () => api.getPage<Notification>('/api/v1/notifications', { size: 5 }),
  markAsRead: (id: string) => api.patch<void>(`/api/v1/notifications/${id}/read`),
  markAllAsRead: () => api.patch<void>('/api/v1/notifications/read-all'),
}
