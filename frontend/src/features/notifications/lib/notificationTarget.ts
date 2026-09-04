import { masterTableDetailPath, tableDetailPath } from '@/config/paths'
import type { AppContext } from '@/stores/contextStore'

import type { Notification } from '../types'

/**
 * Where clicking a notification should go, resolved from its `relatedEntityType` and
 * `relatedEntityId`. Null-safe by construction: a notification that points nowhere is not a bug,
 * it is one that simply has nothing to open.
 */
export interface NotificationTarget {
  context: AppContext
  path: string
}

/** Where each kind of notification leads, and which context the reader has to be in to see it (decisiones.md #156). */
export function notificationTarget(notification: Notification): NotificationTarget | null {
  if (notification.relatedEntityType !== 'game_table' || !notification.relatedEntityId) {
    return null
  }
  switch (notification.notificationType) {
    case 'RegistrationAccepted':
    case 'RegistrationRejected':
      return { context: 'player', path: tableDetailPath(notification.relatedEntityId) }
    // The public detail and not `/my/tables/:id`, because the same notice reaches a candidate and a
    // player alike: a candidate has no `/my/tables` entry for a table they are not in yet, and the
    // public detail shows both of them what the table is asking of them (#63, #206).
    case 'TaskPublished':
      return { context: 'player', path: tableDetailPath(notification.relatedEntityId) }
    case 'NewCandidate':
      return { context: 'master', path: masterTableDetailPath(notification.relatedEntityId) }
    default:
      return null
  }
}
