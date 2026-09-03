import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import type { RegistrationStatus } from '../types'

const STATE_CLASSES: Record<RegistrationStatus, { badge: string; dot: string }> = {
  Candidate: { badge: 'bg-state-pending-bg text-state-pending-fg', dot: 'bg-state-pending-dot' },
  Player: { badge: 'bg-state-open-bg text-state-open-fg', dot: 'bg-state-open-dot' },
  Rejected: { badge: 'bg-state-canceled-bg text-state-canceled-fg', dot: 'bg-state-canceled-dot' },
}

/**
 * Where an application stands, as a badge. Its variants come from a `Record` over the status union,
 * so adding a status is a compile error here rather than a badge that silently renders unstyled.
 *
 * @param props.status the application's status
 */
export function RegistrationStatusBadge({ status }: { status: RegistrationStatus }) {
  const { t } = useTranslation('registrations')
  const classes = STATE_CLASSES[status]

  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium', classes.badge)}>
      <span className={cn('size-1.5 rounded-full', classes.dot)} />
      {t(`status.${status}`)}
    </span>
  )
}
