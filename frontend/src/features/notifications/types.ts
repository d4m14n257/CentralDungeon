/**
 * Mirror of NotificationParams — the names a notification's sentence needs filled in (#197).
 *
 * Every field is optional because each notification type fills in a different subset: a clash names
 * two tables, a new candidate names a person and a table, an acceptance names only the table.
 */
export interface NotificationParams {
  tableName?: string | null
  otherTableName?: string | null
  actorName?: string | null
  /** What a table is asking for, so the bell names the request rather than announcing one (#77). */
  taskTitle?: string | null
}

/**
 * Espejo de NotificationResponse.
 *
 * **It carries no rendered sentence** (#197): `notificationType` plus `params` is what the frontend
 * turns into words, in the reader's language. `title` and `message` are the frozen text of a row
 * written before that change, and they are the fallback for exactly those rows — see
 * `lib/notificationText.ts`.
 */
export interface Notification {
  id: string
  notificationType: string
  params: NotificationParams | null
  title: string | null
  message: string | null
  relatedEntityType: string | null
  relatedEntityId: string | null
  readStatus: 'Unread' | 'Read'
  createdAt: string
}
