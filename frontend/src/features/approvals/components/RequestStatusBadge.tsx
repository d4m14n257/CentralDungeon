import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import type { ApprovalStatus } from '../types'

/**
 * Colour is never the only carrier of meaning (frontend-diseno.md §3): always a dot **and** a label.
 * The classes are written out in full and statically — Tailwind 4 scans the source for literals and
 * cannot see a class name built from a template string.
 */
const STATE_CLASSES: Record<ApprovalStatus, { badge: string; dot: string }> = {
  Pending: { badge: 'bg-state-pending-bg text-state-pending-fg', dot: 'bg-state-pending-dot' },
  Approved: { badge: 'bg-state-open-bg text-state-open-fg', dot: 'bg-state-open-dot' },
  Rejected: { badge: 'bg-state-canceled-bg text-state-canceled-fg', dot: 'bg-state-canceled-dot' },
}

/**
 * Where a request stands, as a badge. Its variants come from a `Record` over {@link ApprovalStatus},
 * so a new state cannot be added without deciding how it looks (arquitectura.md §3.2, regla 9).
 *
 * `Pending` borrows the same family as a table waiting for review, on purpose: on an admin's screen
 * the two mean the same thing — somebody has to look at this.
 *
 * @param props.status where the request stands
 */
export function RequestStatusBadge({ status }: { status: ApprovalStatus }) {
  const { t } = useTranslation('admin')
  const classes = STATE_CLASSES[status]

  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium', classes.badge)}>
      <span className={cn('size-1.5 rounded-full', classes.dot)} />
      {t(`requests.status.${status}`)}
    </span>
  )
}
