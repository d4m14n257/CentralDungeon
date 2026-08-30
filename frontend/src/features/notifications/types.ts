/** Espejo de NotificationResponse. */
export interface Notification {
  id: string
  notificationType: string
  title: string
  message: string | null
  relatedEntityType: string | null
  relatedEntityId: string | null
  readStatus: 'Unread' | 'Read'
  createdAt: string
}
