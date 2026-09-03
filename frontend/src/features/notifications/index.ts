/**
 * Public surface of the notifications feature (#114). Anything not listed here is private to it -
 * from outside, the import is always `@/features/notifications`, never a path inside.
 */

export { NotificationBell } from './components/NotificationBell'
export { useMarkAllAsRead } from './api/useMarkAllAsRead'
export { useMarkAsRead } from './api/useMarkAsRead'
export { useNotifications } from './api/useNotifications'
export { useNotificationClick } from './hooks/useNotificationClick'
/** The feature's domain types. Each is written once in `types.ts` and derived from there (#3.2). */
export type { Notification } from './types'
