import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import type { TableSessionStatus } from '../types'

/**
 * Colour is never the only carrier of information (frontend-diseno.md §3): always a dot plus a
 * label. The class names are complete and static on purpose — Tailwind 4 scans the source for
 * literals and cannot see a class name built out of a template string.
 */
const STATE_CLASSES: Record<TableSessionStatus, { badge: string; dot: string }> = {
  Scheduled: { badge: 'bg-state-open-bg text-state-open-fg', dot: 'bg-state-open-dot' },
  Held: { badge: 'bg-state-done-bg text-state-done-fg', dot: 'bg-state-done-dot' },
  Cancelled: { badge: 'bg-state-canceled-bg text-state-canceled-fg', dot: 'bg-state-canceled-dot' },
}

/**
 * A session's status, as a badge. Its variants come from a `Record` over `TableSessionStatus`, so a
 * new status cannot be added without deciding how it looks (§3.2 regla 9).
 *
 * @param props.status the session's status
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
