import { InboxIcon, MegaphoneIcon, ScrollTextIcon, SendIcon, SwordsIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import type { FileCategory } from '../types'

/**
 * The icon each cajón is recognised by, before its label is read.
 *
 * A `Record` over `FileCategory` rather than a lookup with a fallback (§3.2 regla 9): a sixth kind
 * cannot be added to the union without the compiler asking how it looks.
 */
const CATEGORY_ICONS: Record<FileCategory, ReactNode> = {
  TableMaterial: <SwordsIcon className="size-3.5" />,
  MasterRequest: <ScrollTextIcon className="size-3.5" />,
  PlayerApplication: <SendIcon className="size-3.5" />,
  PlayerSubmission: <InboxIcon className="size-3.5" />,
  Announcement: <MegaphoneIcon className="size-3.5" />,
}

interface FileCategoryBadgeProps {
  /** The cajón (#233). */
  category: FileCategory
  /** Extra classes for the caller's layout. */
  className?: string
}

/**
 * Which flow a file belongs to (#233), as a badge.
 *
 * It says **where the file is used**, never what the document is — nobody can declare that a PDF is a
 * character sheet template, but the system always knows the file came in through a table, an
 * application or an answer. A file in two cajones gets two of these, because membership is a list.
 *
 * **Quiet on purpose.** It sits next to `FileTypeBadge`, which carries the state colour; two
 * coloured badges side by side would compete instead of reading as "kept · character sheet". The
 * icon is `aria-hidden` — the label beside it is the accessible name, and announcing both would say
 * everything twice.
 *
 * @param props.category  the cajón
 * @param props.className extra classes for the caller's layout
 */
export function FileCategoryBadge({ category, className }: FileCategoryBadgeProps) {
  const { t } = useTranslation('files')

  return (
    <span className={cn('border-border text-fg-muted inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-xs', className)}>
      <span aria-hidden="true" className="inline-flex">
        {CATEGORY_ICONS[category]}
      </span>
      {t(`category.${category}`)}
    </span>
  )
}
