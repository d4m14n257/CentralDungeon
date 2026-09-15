import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import type { AccountStatus } from '../types'

/**
 * Colour is never the only carrier of meaning (frontend-diseno.md §3): always a dot **and** a label.
 * The classes are written out in full and statically — Tailwind 4 scans the source for literals and
 * cannot see a class name built from a template string.
 */
const STATE_CLASSES: Record<AccountStatus, { badge: string; dot: string }> = {
  Allowed: { badge: 'bg-state-open-bg text-state-open-fg', dot: 'bg-state-open-dot' },
  Blocked: { badge: 'bg-state-blocked-bg text-state-blocked-fg', dot: 'bg-state-blocked-dot' },
  Deleted: { badge: 'bg-state-draft-bg text-state-draft-fg', dot: 'bg-state-draft-dot' },
}

/**
 * An account's status, as a badge. Only `/admin/users` ever shows one: everywhere else the only
 * status anybody can encounter is `Allowed`, because a blocked account cannot sign in and a deleted
 * one is not returned.
 *
 * Its variants come from a `Record` over `AccountStatus`, so a new status cannot be added without
 * deciding how it looks (arquitectura.md §3.2, regla 9). `Blocked` gets its own token family rather
 * than borrowing `canceled`: a closed account and a cancelled table are not the same event, and the
 * two appear on admin screens next to each other.
 *
 * @param props.status the account's status
 */
export function UserStatusBadge({ status }: { status: AccountStatus }) {
  const { t } = useTranslation('admin')
  const classes = STATE_CLASSES[status]

  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium', classes.badge)}>
      <span className={cn('size-1.5 rounded-full', classes.dot)} />
      {t(`users.status.${status}`)}
    </span>
  )
}
