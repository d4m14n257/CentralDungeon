import { useTranslation } from 'react-i18next'

import { StatusBadge, type StatusTone } from '@/components/StatusBadge'

import type { ApprovalStatus } from '../types'

/**
 * Which tone each state wears. **The map stays here and not in `StatusBadge`**: which of this
 * feature's states counts as "open" is a decision about this domain, and a shared component that knew
 * it would be the wrong kind of shared (`arquitectura.md` §3.1.2).
 */
const STATE_TONES: Record<ApprovalStatus, StatusTone> = {
  Pending: 'pending',
  Approved: 'open',
  Rejected: 'canceled',
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
  return <StatusBadge tone={STATE_TONES[status]} label={t(`requests.status.${status}`)} />
}
