import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { masterTableDetailPath, myTableDetailPath } from '@/config/paths'
import { TableStatusBadge, WeeklyScheduleGrid, useMySchedule } from '@/features/tables'
import type { WeeklyCommitment } from '@/features/tables'
import { browserTimeZone, formatWeekBlock } from '@/lib/date'

/**
 * Where a commitment leads, which is not the same screen for the two roles: a master goes to the
 * table they run, a player to their own view of it.
 */
function destinationOf(commitment: WeeklyCommitment): string {
  return commitment.role === 'Master' ? masterTableDetailPath(commitment.tableId) : myTableDetailPath(commitment.tableId)
}

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
 *
 * **Every block is a shortcut**: clicking a rectangle opens its table. Looking at your week and
 * going to one of its tables is the same errand, and making somebody read a name off the grid to
 * then find it again in a list underneath is work the screen can do for them.
 *
 * The colour says the **role** and nothing else, so the legend is two fixed items rather than one
 * per table — it was doing two jobs at once, explaining the colours and indexing the tables, and
 * only the first is what a colour can carry. The index is the list below, which is one list and not
 * two: a table with no agenda yet is still a table of this week, just without an hour.
 */
export function MySchedulePage() {
  const { t, i18n } = useTranslation('tables')
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
          <WeeklyScheduleGrid commitments={scheduled} timeZone={timeZone} linkFor={destinationOf} />

          {/* Two items, because two is how much the colour says. */}
          <div className="text-fg-muted flex flex-wrap gap-x-5 gap-y-2 text-sm">
            <span className="flex items-center gap-2">
              <span aria-hidden className="bg-brand-500/85 inline-block size-3 rounded-sm" />
              {t('schedule.legendMaster')}
            </span>
            <span className="flex items-center gap-2">
              <span aria-hidden className="bg-state-active-bg inline-block size-3 rounded-sm" />
              {t('schedule.legendPlayer')}
            </span>
          </div>

          <section className="space-y-2">
            <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('schedule.tablesTitle')}</h2>
            <ul className="divide-border divide-y rounded-lg border">
              {data.map((commitment) => (
                <li key={commitment.tableId} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-2.5">
                  <Link to={destinationOf(commitment)} className="min-w-0 flex-1 truncate hover:underline">
                    {commitment.tableName}
                  </Link>
                  <span className="text-fg-subtle shrink-0 text-xs">{t(`schedule.role.${commitment.role}`)}</span>
                  {commitment.blocks.length > 0 ? (
                    // Where the block is too small to read its own label, which is every one-hour table.
                    <span className="text-fg-muted shrink-0 text-xs tabular-nums">
                      {commitment.blocks.map((block) => formatWeekBlock(block, i18n.language, timeZone)).join(' · ')}
                    </span>
                  ) : (
                    <span className="text-fg-subtle shrink-0 text-xs italic">{t('schedule.withoutAgenda')}</span>
                  )}
                  <TableStatusBadge status={commitment.status} />
                </li>
              ))}
            </ul>
          </section>
        </>
      )}
    </div>
  )
}

export { MySchedulePage as Component }
