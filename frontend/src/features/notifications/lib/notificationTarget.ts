import { masterTableDetailPath, tableDetailPath } from '@/config/paths'
import type { AppContext } from '@/stores/contextStore'

import type { Notification } from '../types'

export interface NotificationTarget {
  context: AppContext
  path: string
}

/** A dónde manda cada tipo de notificación, y en qué contexto hay que estar parado para verlo (decisiones.md #156). */
export function notificationTarget(notification: Notification): NotificationTarget | null {
  if (notification.relatedEntityType !== 'game_table' || !notification.relatedEntityId) {
    return null
  }
  switch (notification.notificationType) {
    case 'RegistrationAccepted':
    case 'RegistrationRejected':
      return { context: 'player', path: tableDetailPath(notification.relatedEntityId) }
    case 'NewCandidate':
      return { context: 'master', path: masterTableDetailPath(notification.relatedEntityId) }
    default:
      return null
  }
}
