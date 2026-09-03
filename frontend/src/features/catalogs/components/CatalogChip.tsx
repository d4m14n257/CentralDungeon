import { XIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import type { CatalogValue } from '../types'

/** What a chip needs to know about the value it shows. */
export interface CatalogChipProps {
  /** The value, as its author wrote it. */
  value: CatalogValue
  /** Called with the value's id when the person removes it. Omit for a read-only chip. */
  onRemove?: (id: string) => void
}

/**
 * One catalog value on a table: a system, a tag, a platform.
 *
 * Two rules of the catalog design meet in this component, and both are visible rather than implied:
 *
 * - **It shows the alias its master chose, never the group's canonical entry** (#58). The
 *   equivalence works in the search, not in the presentation: rewriting "DANDD" to "D&D 5e" on
 *   screen would erase a choice that said something.
 * - **A value still waiting for review is dimmed and says so** (#57). The master sees their table
 *   with a tag the other players cannot see yet, and an interface that did not admit that would be
 *   lying about what is published.
 *
 * @param props.value    the value to show
 * @param props.onRemove called with its id when removed; omit to render it read-only
 */
export function CatalogChip({ value, onRemove }: CatalogChipProps) {
  const { t } = useTranslation('catalogs')
  const isPending = value.status === 'Created'

  return (
    <span
      className={cn(
        'border-border inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-xs',
        isPending && 'border-dashed opacity-70',
      )}
      title={isPending ? t('chip.pendingHint') : undefined}
    >
      {value.name}
      {isPending && <span className="text-fg-subtle">{t('chip.pending')}</span>}
      {onRemove && (
        <button
          type="button"
          onClick={() => onRemove(value.id)}
          aria-label={t('chip.remove', { name: value.name })}
          className="text-fg-muted hover:text-fg focus-visible:ring-ring rounded-sm focus-visible:ring-2 focus-visible:outline-none"
        >
          <XIcon className="size-3" aria-hidden="true" />
        </button>
      )}
    </span>
  )
}
