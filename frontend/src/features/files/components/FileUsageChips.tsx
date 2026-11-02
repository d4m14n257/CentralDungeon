import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { masterTableFilesPath, myTableDetailPath } from '@/config/paths'

import type { FileUsage } from '../types'

interface FileUsageChipsProps {
  /** Where the file is being used (ADR-14 §3). An empty array is a meaningful answer, not a missing one. */
  usages: FileUsage[]
}

/**
 * Where a file is being used, as chips (ADR-14 §3).
 *
 * **This is the classification, shown.** The origin of a file — a player's, a master's — is not a
 * column on the row but a fact about each place it is used, because the same character sheet can be
 * attached to a table *and* handed in to a task, and one row cannot be honest about both. So the
 * screen shows the uses rather than a label, and a file with two of them shows two.
 *
 * **The empty case is the point of the component, not its degenerate state.** A file nothing points
 * at is the one the purge of ADR-13 §3 will reach first, and saying so plainly — "sin usar" — is what lets
 * somebody notice before it goes quiet, without the screen ever having to explain retention.
 *
 * **Each table links to the reader's own view of it** (ADR-14 §3): the master's Files tab for a table
 * they run, the player's table for one they play at, and the plain name for neither. The backend says
 * which, because directing a table is not deduced from the role.
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
      {usages.map((usage) => {
        const href = usageHref(usage)
        // `tableName` and not `context`: i18next reserves `context` for its own key resolution,
        // so passing the table's name under it would look up `usage.Table_Hijos del Vacío`.
        const text = t(`usage.${usage.category}`, { tableName: usage.contextName })
        const chip = 'bg-muted text-fg-muted inline-flex items-center rounded-md px-2 py-0.5 text-xs'
        return href === null ? (
          <span key={`${usage.category}-${usage.contextId}`} className={chip}>
            {text}
          </span>
        ) : (
          <Link key={`${usage.category}-${usage.contextId}`} to={href} className={`${chip} hover:underline`}>
            {text}
          </Link>
        )
      })}
    </div>
  )
}

/** The reader's own view of the use's table, or null when they are joined to it as neither master nor player. */
function usageHref(usage: FileUsage): string | null {
  if (usage.membership === 'Master') return masterTableFilesPath(usage.contextId)
  if (usage.membership === 'Player') return myTableDetailPath(usage.contextId)
  return null
}
