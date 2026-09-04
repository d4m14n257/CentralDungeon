import { useNavigate } from 'react-router'

import { useContextStore } from '@/stores/contextStore'

import { useMarkAsRead } from '../api/useMarkAsRead'
import { notificationTarget } from '../lib/notificationTarget'
import type { Notification } from '../types'

/**
 * Clicking a notification: it marks it read if it was not, and takes the reader where it points -
 * switching the active context first when that is needed. A Master notification arriving while the
 * reader is in the Player context must not open the management screen with the switcher pointing at
 * the wrong one (decisiones.md #156).
 */
export function useNotificationClick() {
  const navigate = useNavigate()
  const setActiveContext = useContextStore((state) => state.setActiveContext)
  const markAsRead = useMarkAsRead()

  return (notification: Notification) => {
    if (notification.readStatus === 'Unread') {
      markAsRead.mutate(notification.id)
    }
    const target = notificationTarget(notification)
    if (!target) return
    setActiveContext(target.context)
    void navigate(target.path)
  }
}
