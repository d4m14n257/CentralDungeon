import { useTranslation } from 'react-i18next'

import type { FileUsage } from '../types'

interface FileUsageChipsProps {
  /** Where the file is being used (#232). An empty array is a meaningful answer, not a missing one. */
  usages: FileUsage[]
}

/**
 * Where a file is being used, as chips (#232).
 *
 * **This is the classification, shown.** The origin of a file — a player's, a master's — is not a
 * column on the row but a fact about each place it is used, because the same character sheet can be
 * attached to a table *and* handed in to a task, and one row cannot be honest about both. So the
 * screen shows the uses rather than a label, and a file with two of them shows two.
 *
 * **The empty case is the point of the component, not its degenerate state.** A file nothing points
 * at is the one the purge of #75 will reach first, and saying so plainly — "sin usar" — is what lets
 * somebody notice before it goes quiet, without the screen ever having to explain retention.
 *
 * @param props.usages where the file is being used
 */
export function FileUsageChips({ usages }: FileUsageChipsProps) {
  const { t } = useTranslation('files')

  if (usages.length === 0) {
    return <span className="text-fg-muted text-xs">{t('usage.unused')}</span>
  }

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {usages.map((usage) => (
        <span
          key={`${usage.category}-${usage.contextId}`}
          className="bg-muted text-fg-muted inline-flex items-center rounded-md px-2 py-0.5 text-xs"
        >
          {/* `tableName` and not `context`: i18next reserves `context` for its own key resolution,
              so passing the table's name under it would look up `usage.Table_Hijos del Vacío`. */}
          {t(`usage.${usage.category}`, { tableName: usage.contextName })}
        </span>
      ))}
    </div>
  )
}
