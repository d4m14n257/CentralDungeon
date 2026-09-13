import { useTranslation } from 'react-i18next'

import { AttendanceSummaryView } from '@/components/AttendanceSummaryView'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadMore } from '@/components/LoadMore'
import { Skeleton } from '@/components/ui/skeleton'
import { browserTimeZone, formatDate } from '@/lib/date'
import { HelpLink } from '@/features/help'
import { TableStatusBadge, useTableHistory } from '@/features/tables'

/**
 * `/player/history` (#133) - every table this reader played at whose run is over, `Finished` or
 * `Canceled`, with how it ended for them.
 *
 * It exists because `/player/my-tables` stopped answering for these the moment it was scoped to
 * live tables only: a table does not vanish from someone's record the day it closes, it moves here.
 *
 * **An empty list reads as news, not as a problem** (frontend-diseno.md §1 principio 2 read the
 * other way round): nobody who has never finished a table did anything wrong, so the empty state
 * says exactly that and nothing that could be mistaken for an error.
 */
export function PlayerHistoryPage() {
  const { t, i18n } = useTranslation('tables')
  // isLoadingError, not isError: a background refetch that fails must not hide a list that already
  // loaded (docs/decisiones.md #150).
  const { data, isPending, isLoadingError, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useTableHistory()

  const entries = data?.pages.flatMap((page) => page.content) ?? []
  const total = data?.pages[0]?.totalElements ?? 0

  return (
    <div className="space-y-4">
      <h1 className="font-serif text-2xl font-semibold">{t('history.title')}</h1>
      {/* Una mesa que ya no está en «Mis mesas» parece perdida hasta que alguien explica que se mudó
          sola (#133a). */}
      <HelpLink section="players.history" className="inline-block text-xs">
        {t('history.help')}
      </HelpLink>
      {isPending && (
        <div className="space-y-2">
          {Array.from({ length: 3 }, (_, index) => (
            <Skeleton key={index} className="h-20 rounded-lg" />
          ))}
        </div>
      )}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && entries.length === 0 && <EmptyState title={t('history.emptyTitle')} description={t('history.emptyDescription')} />}
      {entries.length > 0 && (
        <>
          <ul className="divide-border divide-y rounded-lg border">
            {entries.map((entry) => (
              <li key={entry.id} className="flex flex-wrap items-start justify-between gap-4 px-4 py-3">
                <div className="min-w-0 space-y-1">
                  <p className="truncate font-medium">{entry.name}</p>
                  <p className="text-fg-subtle text-xs">
                    {/* The date goes through lib/date.ts: locale and zone are parameters, never
                        constants (#111, #192). */}
                    {t('history.closedOn', { date: formatDate(entry.closedAt, i18n.language, browserTimeZone()) })}
                  </p>
                </div>
                <div className="flex flex-col items-end gap-2">
                  <TableStatusBadge status={entry.status} />
                  <AttendanceSummaryView summary={entry.attendance} />
                </div>
              </li>
            ))}
          </ul>
          <LoadMore
            hasMore={hasNextPage}
            isLoading={isFetchingNextPage}
            onLoadMore={() => void fetchNextPage()}
            shown={entries.length}
            total={total}
          />
        </>
      )}
    </div>
  )
}

export { PlayerHistoryPage as Component }
