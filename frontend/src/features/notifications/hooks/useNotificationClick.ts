import { useNavigate } from 'react-router'

import { useContextStore } from '@/stores/contextStore'

import { useMarkAsRead } from '../api/useMarkAsRead'
import { notificationTarget } from '../lib/notificationTarget'
import type { Notification } from '../types'

/**
 * Click en una notificación: la marca leída si no lo estaba, y te lleva a lo que le corresponde -
 * cambiando el contexto activo primero si hace falta (una notificación de Master llegando en
 * contexto Jugador no debe abrir la pantalla de gestión con el selector marcado en el contexto
 * equivocado, decisiones.md #156).
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
