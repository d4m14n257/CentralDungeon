import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import type { FileType } from '../types'

/**
 * Colour is never the only carrier of meaning (frontend-diseno.md 3): always a dot **and** a label.
 * The classes are written out in full and statically on purpose — Tailwind 4 scans the source for
 * literals and cannot see a class name built from a template string.
 */
const STATE_CLASSES: Record<FileType, { badge: string; dot: string }> = {
  Public: { badge: 'bg-state-open-bg text-state-open-fg', dot: 'bg-state-open-dot' },
  Private: { badge: 'bg-state-draft-bg text-state-draft-fg', dot: 'bg-state-draft-dot' },
  SingleUse: { badge: 'bg-state-pending-bg text-state-pending-fg', dot: 'bg-state-pending-dot' },
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
  const classes = STATE_CLASSES[fileType]

  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium', classes.badge)}>
      <span className={cn('size-1.5 rounded-full', classes.dot)} />
      {t(`fileType.${fileType}`)}
    </span>
  )
}
