import { useTranslation } from 'react-i18next'

import { StatusBadge, type StatusTone } from '@/components/StatusBadge'

import type { TableSessionStatus } from '../types'

/**
 * Which tone each state wears. **The map stays here and not in `StatusBadge`**: which of this
 * feature's states counts as "open" is a decision about this domain, and a shared component that knew
 * it would be the wrong kind of shared (`arquitectura.md` §3.1.2).
 */
const STATE_TONES: Record<TableSessionStatus, StatusTone> = {
  Scheduled: 'open',
  Held: 'done',
  Cancelled: 'canceled',
}

/**
 * A session's status, as a badge. Its variants come from a `Record` over `TableSessionStatus`, so a
 * new status cannot be added without deciding how it looks (§3.2 regla 9).
 *
 * @param props.status the session's status
 */
export function SessionStatusBadge({ status }: { status: TableSessionStatus }) {
  const { t } = useTranslation('tables')
  return <StatusBadge tone={STATE_TONES[status]} label={t(`sessions.status.${status}`)} />
}
