/**
 * Public surface of the notifications feature (ADR-19 §1). Anything not listed here is private to it -
 * from outside, the import is always `@/features/notifications`, never a path inside.
 */

export { NotificationBell } from './components/NotificationBell'
export { useMarkAllAsRead } from './api/useMarkAllAsRead'
export { useMarkAsRead } from './api/useMarkAsRead'
export { useNotificationInbox, useNotifications } from './api/useNotifications'
export { useNotificationClick } from './hooks/useNotificationClick'
export { notificationText } from './lib/notificationText'
/** The feature's domain types. Each is written once in `types.ts` and derived from there (arquitectura §3.2). */
export { NOTIFICATION_TYPES } from './types'
export type { Notification, NotificationParams, NotificationType } from './types'
export type { NotificationText } from './lib/notificationText'
