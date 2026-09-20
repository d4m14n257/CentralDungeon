import { useTranslation } from 'react-i18next'

import { StatusBadge, type StatusTone } from '@/components/StatusBadge'

import type { RegistrationStatus } from '../types'

/**
 * Which tone each status wears. **The map stays here and not in `StatusBadge`**: which of this
 * feature's statuses counts as "open" is a decision about this domain (`arquitectura.md` §3.1.2).
 */
const STATE_TONES: Record<RegistrationStatus, StatusTone> = {
  Candidate: 'pending',
  Player: 'open',
  Rejected: 'canceled',
  // Its own tone and not `canceled`, though both are refusals: they sit next to each other in the
  // same list and mean different things - one is "you never got in", the other is "you were in and
  // were removed". Two rows the reader tells apart at a glance cannot share a swatch.
  Blocked: 'blocked',
}

/**
 * Where an application stands, as a badge. Its variants come from a `Record` over the status union,
 * so adding a status is a compile error here rather than a badge that silently renders unstyled.
 *
 * @param props.status the application's status
 */
export function RegistrationStatusBadge({ status }: { status: RegistrationStatus }) {
  const { t } = useTranslation('registrations')
  return <StatusBadge tone={STATE_TONES[status]} label={t(`status.${status}`)} />
}
