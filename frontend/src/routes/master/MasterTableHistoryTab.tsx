import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router'

import { ErrorState } from '@/components/ErrorState'
import { EmptyState } from '@/components/EmptyState'
import { Skeleton } from '@/components/ui/skeleton'
import { useTableStatusHistory } from '@/features/tables'

/**
 * A table's lifecycle history, oldest first: who moved it, where to, and why.
 *
 * Exported for the admin's history tab (ADR-06 §8), which shows the same history next to the admin's own
 * actions instead of the master's transitions.
 *
 * @param props.tableId the table
 */
export function StatusTimeline({ tableId }: { tableId: string }) {
  const { t, i18n } = useTranslation('master')
  const { data, isPending, isLoadingError, refetch } = useTableStatusHistory(tableId)

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
            <span className="text-fg-subtle text-xs">{new Date(change.createdAt).toLocaleString(i18n.language)}</span>
          </div>
          <p className="text-fg-muted text-xs">{t('status.changedBy', { name: change.changedByName })}</p>
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
  const { tableId } = useOutletContext<{ tableId: string }>()
  return <StatusTimeline tableId={tableId} />
}

export { MasterTableHistoryTab as Component }
