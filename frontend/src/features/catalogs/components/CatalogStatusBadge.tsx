import { useTranslation } from 'react-i18next'

import { StatusBadge, type StatusTone } from '@/components/StatusBadge'

import type { CatalogStatus } from '../types'

/**
 * Which tone each state wears. **The map stays here and not in `StatusBadge`**: which of this
 * feature's states counts as "open" is a decision about this domain, and a shared component that knew
 * it would be the wrong kind of shared (`arquitectura.md` §3.1.2).
 */
const STATE_TONES: Record<CatalogStatus, StatusTone> = {
  Created: 'pending',
  Accepted: 'open',
  Rejected: 'canceled',
  Disabled: 'draft',
}

/**
 * A catalog value's lifecycle state, as a badge. Only /admin/catalogs shows it in full - everywhere
 * else the only status a player can even encounter is `Accepted`.
 *
 * Its variants come from a `Record` over `CatalogStatus`, so a new state cannot be added without
 * deciding how it looks (#3.2 regla 9).
 *
 * @param props.status the value's status
 */
export function CatalogStatusBadge({ status }: { status: CatalogStatus }) {
  const { t } = useTranslation('catalogs')
  return <StatusBadge tone={STATE_TONES[status]} label={t(`status.${status}`)} />
}
