import { useNavigate } from 'react-router'

import { useMarkAsRead } from '../api/useMarkAsRead'
import { notificationTarget } from '../lib/notificationTarget'
import type { Notification } from '../types'

/**
 * Clicking a notification: it marks it read if it was not, and takes the reader where it points.
 *
 * It no longer has to switch the context on the way: a master notification opens a `/master/*` path
 * and the header reads the context from there (#222), which is what #156 was working around when the
 * chip still tracked a remembered value.
 *
 * @returns the click handler, ready to hand to a notification row
 */
export function useNotificationClick() {
  const navigate = useNavigate()
  const markAsRead = useMarkAsRead()

  return (notification: Notification) => {
    if (notification.readStatus === 'Unread') {
      markAsRead.mutate(notification.id)
    }
    const path = notificationTarget(notification)
    if (!path) return
    void navigate(path)
  }
}
