import { useTranslation } from 'react-i18next'

import { StatusBadge, type StatusTone } from '@/components/StatusBadge'

import type { GameTableStatus } from '../types'

/**
 * Which tone each state wears. **The map stays here and not in `StatusBadge`**: which of this
 * feature's states counts as "open" is a decision about this domain, and a shared component that knew
 * it would be the wrong kind of shared (`arquitectura.md` §3.1.2).
 */
const STATE_TONES: Record<GameTableStatus, StatusTone> = {
  Draft: 'draft',
  Unassigned: 'draft',
  Preparation: 'pending',
  ChangesRequested: 'warning',
  Opened: 'open',
  InProgress: 'active',
  PauseRequested: 'pending',
  Pause: 'paused',
  Finished: 'done',
  Canceled: 'canceled',
}

/**
 * A table's lifecycle status, as a badge. Its variants come from a `Record` over
 * `GameTableStatus`, so a new state cannot be added without deciding how it looks (#3.2 regla 9).
 *
 * @param props.status the table's status
 */
export function TableStatusBadge({ status }: { status: GameTableStatus }) {
  const { t } = useTranslation('tables')
  return <StatusBadge tone={STATE_TONES[status]} label={t(`status.${status}`)} />
}
