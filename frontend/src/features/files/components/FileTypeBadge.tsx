import { useTranslation } from 'react-i18next'

import { StatusBadge, type StatusTone } from '@/components/StatusBadge'

import type { FileType } from '../types'

/**
 * Which tone each state wears. **The map stays here and not in `StatusBadge`**: which of this
 * feature's states counts as "open" is a decision about this domain, and a shared component that knew
 * it would be the wrong kind of shared (`arquitectura.md` §3.1.2).
 */
const STATE_TONES: Record<FileType, StatusTone> = {
  Public: 'open',
  Private: 'draft',
  SingleUse: 'pending',
}

/**
 * Which lifecycle a file has (#68), as a badge.
 *
 * It answers a question people actually ask of a row: is this mine and kept, is it the platform's, or
 * is it something that will be reclaimed once nobody uses it (#75)? Its variants come from a `Record`
 * over `FileType`, so a fourth kind cannot be added without deciding how it looks (#3.2 regla 9).
 *
 * @param props.fileType which lifecycle the file has
 */
export function FileTypeBadge({ fileType }: { fileType: FileType }) {
  const { t } = useTranslation('files')
  return <StatusBadge tone={STATE_TONES[fileType]} label={t(`fileType.${fileType}`)} />
}
