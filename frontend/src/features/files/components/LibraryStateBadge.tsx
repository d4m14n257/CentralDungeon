import { useTranslation } from 'react-i18next'

import { StatusBadge, type StatusTone } from '@/components/StatusBadge'

import type { LibraryState } from '../libraryState'

/**
 * Which tone each state wears. Here and not in `StatusBadge`: which of the library's states counts
 * as "open" is this domain's decision (arquitectura §3.1.2).
 */
const STATE_TONES: Record<LibraryState, StatusTone> = {
  Unpublished: 'draft',
  Published: 'open',
  Hidden: 'paused',
  Removed: 'canceled',
}

/**
 * Where a file stands in the platform's library, as a badge (#282): the column that tells an admin
 * whether masters can see it, whether it waits, or whether it was taken out of the pickers.
 *
 * @param props.state where the file stands
 */
export function LibraryStateBadge({ state }: { state: LibraryState }) {
  const { t } = useTranslation('files')
  return <StatusBadge tone={STATE_TONES[state]} label={t(`libraryState.${state}`)} />
}
