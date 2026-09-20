import { useTranslation } from 'react-i18next'

import { StatusBadge, type StatusTone } from '@/components/StatusBadge'

import type { AccountStatus } from '../types'

/**
 * Which tone each status wears. **The map stays here and not in `StatusBadge`**: which of this
 * feature's statuses counts as "open" is a decision about this domain (`arquitectura.md` §3.1.2).
 */
const STATE_TONES: Record<AccountStatus, StatusTone> = {
  Allowed: 'open',
  Blocked: 'blocked',
  Deleted: 'draft',
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
  return <StatusBadge tone={STATE_TONES[status]} label={t(`users.status.${status}`)} />
}
