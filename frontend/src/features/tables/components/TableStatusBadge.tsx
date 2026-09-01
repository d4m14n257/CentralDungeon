import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import type { GameTableStatus } from '../types'

/**
 * El color nunca es el único portador de información (frontend-diseno.md 3): siempre punto +
 * etiqueta. Clases completas y estáticas a propósito - Tailwind 4 escanea el código fuente
 * buscando literales, no puede ver nombres de clase construidos con template strings.
 */
const STATE_CLASSES: Record<GameTableStatus, { badge: string; dot: string }> = {
  Unassigned: { badge: 'bg-state-draft-bg text-state-draft-fg', dot: 'bg-state-draft-dot' },
  Preparation: { badge: 'bg-state-pending-bg text-state-pending-fg', dot: 'bg-state-pending-dot' },
  ChangesRequested: { badge: 'bg-state-warning-bg text-state-warning-fg', dot: 'bg-state-warning-dot' },
  Opened: { badge: 'bg-state-open-bg text-state-open-fg', dot: 'bg-state-open-dot' },
  InProgress: { badge: 'bg-state-active-bg text-state-active-fg', dot: 'bg-state-active-dot' },
  PauseRequested: { badge: 'bg-state-pending-bg text-state-pending-fg', dot: 'bg-state-pending-dot' },
  Pause: { badge: 'bg-state-paused-bg text-state-paused-fg', dot: 'bg-state-paused-dot' },
  Finished: { badge: 'bg-state-done-bg text-state-done-fg', dot: 'bg-state-done-dot' },
  Canceled: { badge: 'bg-state-canceled-bg text-state-canceled-fg', dot: 'bg-state-canceled-dot' },
}

export function TableStatusBadge({ status }: { status: GameTableStatus }) {
  const { t } = useTranslation('tables')
  const classes = STATE_CLASSES[status]

  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium', classes.badge)}>
      <span className={cn('size-1.5 rounded-full', classes.dot)} />
      {t(`status.${status}`)}
    </span>
  )
}
