import { api } from '@/api/client'
import { pageSize } from '@/config/pagination'

import type { Notification } from '../types'

/** How many the bell shows: titles only, a glance and not the inbox (ADR-18 §5). */
const BELL_SIZE = 5

/** Newest first, already ordered by the backend. */
export const notificationsApi = {
  /** The bell's latest 5. */
  recent: () => api.getPage<Notification>('/api/v1/notifications', { size: BELL_SIZE }),
  /** One page of the full inbox (ADR-04 §6). */
  list: (page = 0) => api.getPage<Notification>('/api/v1/notifications', { page, size: pageSize.list }),
  markAsRead: (id: string) => api.patch<void>(`/api/v1/notifications/${id}/read`),
  markAllAsRead: () => api.patch<void>('/api/v1/notifications/read-all'),
}
