import { IdCard } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router'

import { ErrorState } from '@/components/ErrorState'
import { EmptyState } from '@/components/EmptyState'
import { IconAction } from '@/components/IconAction'
import { Skeleton } from '@/components/ui/skeleton'
import { useTableStatusHistory } from '@/features/tables'
import type { MasterSummary } from '@/features/tables'
import { browserTimeZone, formatDateTime } from '@/lib/date'

/**
 * A table's lifecycle history, oldest first: who moved it, where to, and why.
 *
 * Exported for the admin's history tab (ADR-06 §8), which shows the same history next to the admin's own
 * actions instead of the master's transitions.
 *
 * @param props.tableId        the table
 * @param props.onOpenPerson opens the card of whoever made a change; the screen that mounts the tab decides
 *                           what that is (ADR-10 §6). Absent, the name is plain text
 * @param props.canOpen      which of those people the reader may open. Absent, every one of them
 */
export function StatusTimeline({
  tableId,
  onOpenPerson,
  canOpen,
}: {
  tableId: string
  onOpenPerson?: ((userId: string) => void) | undefined
  canOpen?: ((userId: string) => boolean) | undefined
}) {
  const { t, i18n } = useTranslation('master')
  const { data, isPending, isLoadingError, refetch } = useTableStatusHistory(tableId)
  const timeZone = browserTimeZone()

  if (isPending) {
    return <Skeleton className="h-24 w-full" />
  }
  if (isLoadingError) {
    return <ErrorState onRetry={() => void refetch()} />
  }
  if (data.length === 0) {
    return <EmptyState title={t('status.historyEmptyTitle')} />
  }

  return (
    <ol className="list-divided">
      {data.map((change) => (
        <li key={change.id} className="space-y-1 px-4 py-3 text-sm">
          <div className="flex items-center justify-between gap-4">
            <span>
              {t(`status.${change.fromStatus}` as const, { ns: 'tables', defaultValue: change.fromStatus })} →{' '}
              {t(`status.${change.toStatus}` as const, { ns: 'tables', defaultValue: change.toStatus })}
            </span>
            <span className="text-fg-subtle text-xs">{formatDateTime(change.createdAt, i18n.language, timeZone)}</span>
          </div>
          <div className="flex items-center gap-1">
            <p className="text-fg-muted text-xs">{t('status.changedBy', { name: change.changedByName })}</p>
            {onOpenPerson && (canOpen?.(change.changedById) ?? true) && (
              <IconAction
                icon={<IdCard className="size-4" />}
                label={t('people.viewRecord', { name: change.changedByName })}
                onClick={() => onOpenPerson(change.changedById)}
              />
            )}
          </div>
          {change.justification && <p className="text-fg text-xs italic">"{change.justification}"</p>}
        </li>
      ))}
    </ol>
  )
}

/**
 * The History tab of `/master/tables/:id` (ADR-21 §10): every step of the table's life, who took it and why.
 *
 * **Reading, not deciding.** It was the "Status" tab and carried the master's transitions too; those
 * moved to `MasterTableStatusNotice` above the tabs, where they are seen from every tab, and what is
 * left is the history - the same split the admin's detail already had.
 */
export function MasterTableHistoryTab() {
  const { tableId, masters, onOpenPerson } = useOutletContext<{
    tableId: string
    masters: MasterSummary[]
    onOpenPerson?: (userId: string) => void
  }>()
  // A master may read the card of a co-master, but not of an admin who acted on the table without running it:
  // that card answers "not found", so its name stays plain text instead of opening an error.
  const runsTable = (userId: string) => masters.some((master) => master.userId === userId)
  return <StatusTimeline tableId={tableId} onOpenPerson={onOpenPerson} canOpen={runsTable} />
}

export { MasterTableHistoryTab as Component }
