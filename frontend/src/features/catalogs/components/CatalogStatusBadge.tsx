import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import type { CatalogStatus } from '../types'

/**
 * Colour is never the only carrier of meaning (frontend-diseno.md 3): always a dot **and** a label.
 * The classes are written out in full and statically on purpose - Tailwind 4 scans the source for
 * literals and cannot see a class name built from a template string.
 */
const STATE_CLASSES: Record<CatalogStatus, { badge: string; dot: string }> = {
  Created: { badge: 'bg-state-pending-bg text-state-pending-fg', dot: 'bg-state-pending-dot' },
  Accepted: { badge: 'bg-state-open-bg text-state-open-fg', dot: 'bg-state-open-dot' },
  Rejected: { badge: 'bg-state-canceled-bg text-state-canceled-fg', dot: 'bg-state-canceled-dot' },
  Disabled: { badge: 'bg-state-draft-bg text-state-draft-fg', dot: 'bg-state-draft-dot' },
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
  const classes = STATE_CLASSES[status]

  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium', classes.badge)}>
      <span className={cn('size-1.5 rounded-full', classes.dot)} />
      {t(`status.${status}`)}
    </span>
  )
}
