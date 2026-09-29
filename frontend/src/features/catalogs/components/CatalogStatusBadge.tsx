import { useTranslation } from 'react-i18next'

import { StatusBadge } from '@/components/StatusBadge'

import type { CatalogStatus } from '../types'
import { CATALOG_STATUS_TONES } from './catalogStatusTones'

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
  return <StatusBadge tone={CATALOG_STATUS_TONES[status]} label={t(`status.${status}`)} />
}
