import { FileTextIcon, ImageIcon, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { formatFileSize } from '../format'

/** Which icon stands for a MIME type. Images get their own; everything else is a document. */
function iconForMimeType(mimeType: string): LucideIcon {
  return mimeType.startsWith('image/') ? ImageIcon : FileTextIcon
}

interface FileCardProps {
  /** The original filename — metadata, and the only thing most people remember about a file (#80). */
  name: string
  /** The declared MIME type, which decides the icon. */
  mimeType: string
  /** The size **as uploaded**, before compression (#75) — the number the person recognises. */
  sizeBytes: number
  /** Extra lines under the name: badges, uses, when it was last touched. */
  meta?: ReactNode
  /** The row's actions, aligned right. */
  actions?: ReactNode
}

/**
 * One file, as a row that can be read at a glance.
 *
 * **The replacement for a two-line `<li>`**, and what it adds is the context that makes a list of
 * files navigable instead of a list of filenames: an icon that says what kind of thing this is
 * before anything is read, and room under the name for what the caller knows — the category (#233),
 * where it is used (#232), how long since anybody touched it.
 *
 * The name truncates and the row does not: a long filename must not push the actions off the side
 * of a phone. The size sits beside the name rather than under it, so the vertical space belongs to
 * `meta`, which is the part that differs between screens.
 *
 * @param props.name      the original filename
 * @param props.mimeType  the declared MIME type
 * @param props.sizeBytes the size as uploaded
 * @param props.meta      extra lines under the name
 * @param props.actions   the row's actions
 */
export function FileCard({ name, mimeType, sizeBytes, meta, actions }: FileCardProps) {
  const { t, i18n } = useTranslation('files')
  const size = formatFileSize(sizeBytes, i18n.language)
  const Icon = iconForMimeType(mimeType)

  return (
    <div className="flex items-start gap-3 py-3">
      <span aria-hidden="true" className="text-fg-muted mt-0.5 shrink-0">
        <Icon className="size-5" />
      </span>

      <div className="min-w-0 flex-1 space-y-1.5">
        <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
          <p className="text-fg min-w-0 truncate text-sm font-medium">{name}</p>
          <p className="text-fg-muted shrink-0 text-xs">{t(`size.${size.unit}`, { value: size.value })}</p>
        </div>
        {meta}
      </div>

      {actions && <div className="flex shrink-0 items-center gap-1">{actions}</div>}
    </div>
  )
}
