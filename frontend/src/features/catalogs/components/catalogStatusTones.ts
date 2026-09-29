import type { StatusTone } from '@/components/StatusBadge'

import type { CatalogStatus } from '../types'

/**
 * Which tone each state wears. **The map stays here and not in `StatusBadge`**: which of this
 * feature's states counts as "open" is a decision about this domain, and a shared component that knew
 * it would be the wrong kind of shared (`arquitectura` §3.1.2). Exported inside the feature because
 * the canvas nodes (#275) wear the same tones as the badges, and a second map would drift.
 */
export const CATALOG_STATUS_TONES: Record<CatalogStatus, StatusTone> = {
  Created: 'pending',
  Accepted: 'open',
  Rejected: 'canceled',
  Disabled: 'draft',
}
