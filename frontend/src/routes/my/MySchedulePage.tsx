import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { masterTableDetailPath, myTableDetailPath } from '@/config/paths'
import { TableStatusBadge, WeeklyScheduleGrid, useMySchedule } from '@/features/tables'
import { browserTimeZone } from '@/lib/date'

/**
 * `/my/schedule` — the reader's whole week, as a grid (#227).
 *
 * **It belongs to no context, and that is the point.** A person's week is not a master's week or a
 * player's week: the evenings they run and the evenings they play are the same evenings, and the
 * only screen worth having is the one that shows them colliding. Splitting it by context would hide
 * exactly what somebody opens it to see, so it sits beside `/notifications` and `/help` (#222).
 *
 * It draws the same intervals #178 compares to refuse a clash, so a gap here is a gap the server
 * will accept — which is what makes it usable for finding room for a new table.
 */
export function MySchedulePage() {
  const { t } = useTranslation('tables')
  const timeZone = useMemo(() => browserTimeZone(), [])
  // isLoadingError, not isError: a background refetch that fails must not blank a week that already
  // loaded (#150).
  const { data, isPending, isLoadingError, refetch } = useMySchedule()

  if (isPending) {
    return <Skeleton className="h-96 w-full" />
  }

  if (isLoadingError || !data) {
    return <ErrorState onRetry={() => void refetch()} />
  }

  const scheduled = data.filter((commitment) => commitment.blocks.length > 0)
  const withoutAgenda = data.filter((commitment) => commitment.blocks.length === 0)

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="font-serif text-2xl font-semibold">{t('schedule.title')}</h1>
        <p className="text-fg-muted text-sm">{t('schedule.subtitle', { timeZone })}</p>
      </div>

      {data.length === 0 ? (
        <EmptyState title={t('schedule.emptyTitle')} description={t('schedule.emptyDescription')} />
      ) : (
        <>
          <WeeklyScheduleGrid commitments={scheduled} timeZone={timeZone} />

          <ul className="flex flex-wrap gap-x-5 gap-y-2 text-sm">
            {scheduled.map((commitment) => (
              <li key={commitment.tableId} className="flex items-center gap-2">
                <span
                  aria-hidden
                  className={
                    commitment.role === 'Master'
                      ? 'bg-brand-500/85 inline-block size-3 rounded-sm'
                      : 'bg-state-active-bg inline-block size-3 rounded-sm'
                  }
                />
                {/* The block links where the reader can act on the table, which differs by role. */}
                <Link
                  to={commitment.role === 'Master' ? masterTableDetailPath(commitment.tableId) : myTableDetailPath(commitment.tableId)}
                  className="hover:text-brand-fg"
                >
                  {commitment.tableName}
                </Link>
                <span className="text-fg-subtle text-xs">{t(`schedule.role.${commitment.role}`)}</span>
              </li>
            ))}
          </ul>
        </>
      )}

      {withoutAgenda.length > 0 && (
        <section className="space-y-2">
          <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('schedule.withoutAgendaTitle')}</h2>
          <p className="text-fg-muted text-sm">{t('schedule.withoutAgendaDescription')}</p>
          <ul className="divide-border divide-y rounded-lg border">
            {withoutAgenda.map((commitment) => (
              <li key={commitment.tableId} className="flex flex-wrap items-center gap-3 px-4 py-2.5">
                <Link
                  to={commitment.role === 'Master' ? masterTableDetailPath(commitment.tableId) : myTableDetailPath(commitment.tableId)}
                  className="min-w-0 flex-1 truncate hover:underline"
                >
                  {commitment.tableName}
                </Link>
                <span className="text-fg-subtle shrink-0 text-xs">{t(`schedule.role.${commitment.role}`)}</span>
                <TableStatusBadge status={commitment.status} />
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}

export { MySchedulePage as Component }
