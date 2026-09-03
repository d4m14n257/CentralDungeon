import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import type { TableSessionStatus } from '../types'

/**
 * El color nunca es el único portador de información (frontend-diseno.md §3): siempre punto +
 * etiqueta. Clases completas y estáticas a propósito — Tailwind 4 escanea literales en el código
 * fuente y no puede ver un nombre de clase armado con un template string.
 */
const STATE_CLASSES: Record<TableSessionStatus, { badge: string; dot: string }> = {
  Scheduled: { badge: 'bg-state-open-bg text-state-open-fg', dot: 'bg-state-open-dot' },
  Held: { badge: 'bg-state-done-bg text-state-done-fg', dot: 'bg-state-done-dot' },
  Cancelled: { badge: 'bg-state-canceled-bg text-state-canceled-fg', dot: 'bg-state-canceled-dot' },
}

/**
 * El estado de una sesión, como badge. Sus variantes salen de un `Record` sobre
 * `TableSessionStatus`, así que no se puede agregar un estado sin decidir cómo se ve (§3.2 regla 9).
 *
 * @param props.status el estado de la sesión
 */
export function SessionStatusBadge({ status }: { status: TableSessionStatus }) {
  const { t } = useTranslation('tables')
  const classes = STATE_CLASSES[status]

  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium', classes.badge)}>
      <span className={cn('size-1.5 rounded-full', classes.dot)} />
      {t(`sessions.status.${status}`)}
    </span>
  )
}
