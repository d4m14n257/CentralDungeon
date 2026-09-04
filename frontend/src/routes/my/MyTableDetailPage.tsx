import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { Skeleton } from '@/components/ui/skeleton'
import { helpPath, tableDetailPath } from '@/config/paths'
import { FileList, FilePicker } from '@/features/files'
import { AttendanceSummaryView, SessionList, TableStatusBadge, useGameTable, useMySessions, type MySessions } from '@/features/tables'
import { TableTasksSection } from '@/features/tasks'
import { browserTimeZone, formatSlot, utcSlotToLocal } from '@/lib/date'
import { ApiError } from '@/types/api'

/**
 * My calendar and my attendance on this table. Its own block with its own query, not something the
 * page passes down: each block of a composed screen takes an id and fetches its own data (§3.1.5).
 */
function MySessionsSection({ tableId }: { tableId: string }) {
  const { t } = useTranslation('tables')
  // isLoadingError, not isError: see docs/decisiones.md #150.
  const { data, isPending, error, isLoadingError, refetch } = useMySessions(tableId)

  if (isPending) {
    return <Skeleton className="h-40 w-full" />
  }

  if (error instanceof ApiError && error.status === 403) {
    return <ForbiddenState />
  }

  if (isLoadingError || !data) {
    return <ErrorState onRetry={() => void refetch()} />
  }

  const mine: MySessions = data

  return (
    <div className="space-y-6">
      <section className="space-y-2">
        <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('sessions.myAttendanceTitle')}</h2>
        <AttendanceSummaryView summary={mine.summary} />
        {/* The three numbers are explained in the help, under its stable #ref (#137, #167, #168). */}
        <Link to={helpPath('players', 'my-sessions')} className="text-fg-muted inline-block text-xs underline">
          {t('sessions.myAttendanceHelp')}
        </Link>
      </section>

      <section className="space-y-2">
        <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('sessions.myCalendarTitle')}</h2>
        {mine.sessions.length === 0 ? (
          <EmptyState title={t('sessions.myCalendarEmptyTitle')} description={t('sessions.myCalendarEmptyDescription')} />
        ) : (
          <SessionList sessions={mine.sessions} />
        )}
      </section>
    </div>
  )
}

/**
 * `/my/tables/:id` — my table: its agenda, its sessions and **my** attendance, read-only.
 *
 * It is the minimum player-side screen F1 needs to be testable end to end; the rest of it — tasks,
 * files, the other players — arrives with F1.5 and F2.
 *
 * Everything on it is in the reader's own time; what the server stores is UTC (#22).
 */
export function MyTableDetailPage() {
  const { t, i18n } = useTranslation('tables')
  // A second namespace rather than copying the file labels into `tables`: the words belong to the
  // files domain and are the same ones the master's tab shows (regla dura 18).
  const { t: tFiles } = useTranslation('files')
  const { id } = useParams<{ id: string }>()
  const tableId = id ?? ''
  const { data: table, isPending, isLoadingError } = useGameTable(tableId)

  if (isPending) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-1/2" />
        <Skeleton className="h-48 w-full" />
      </div>
    )
  }

  if (isLoadingError || !table) {
    return <ErrorState message={t('detail.notFoundDescription')} />
  }

  const timeZone = browserTimeZone()
  const localSchedule = table.schedule.map((slot) => utcSlotToLocal(slot, timeZone))

  return (
    <div className="border-border-strong bg-surface space-y-6 rounded-xl border p-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="font-serif text-2xl font-semibold">{table.name}</h1>
          <Link to={tableDetailPath(table.id)} className="text-fg-muted text-sm underline">
            {t('sessions.seePublicDetail')}
          </Link>
        </div>
        <TableStatusBadge status={table.status} />
      </div>

      {localSchedule.length > 0 && (
        <section className="border-border border-t pt-4">
          <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('detail.schedule')}</h2>
          <ul className="mt-1.5 space-y-0.5 text-sm">
            {localSchedule.map((slot) => (
              <li key={`${slot.weekday}-${slot.hourtime}`}>{formatSlot(slot, i18n.language, table.duration)}</li>
            ))}
          </ul>
          <p className="text-fg-subtle mt-1 text-xs">{t('detail.scheduleTimeZone', { timeZone })}</p>
        </section>
      )}

      {/* What the table shares (#79). Private attachments never arrive here — the server leaves them
          out of the detail, so the screen has nothing to hide. */}
      <section className="border-border border-t pt-4">
        <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{tFiles('table.readOnlyTitle')}</h2>
        <div className="mt-1.5">
          {table.files.length === 0 ? (
            <EmptyState title={tFiles('table.readOnlyEmptyTitle')} description={tFiles('table.readOnlyEmptyDescription')} />
          ) : (
            <FileList
              files={table.files}
              renderMeta={(file) => <span className="text-fg-muted text-xs">{tFiles(`tableFileType.${file.tableFileType}`)}</span>}
            />
          )}
        </div>
        <Link to={helpPath('players', 'files')} className="text-fg-muted mt-2 inline-block text-xs underline">
          {tFiles('table.helpLink')}
        </Link>
      </section>

      {/* What the table asks of its players, and of me in particular (#63, #76). The block owns its
          own query and its own dialog; this screen only hands it the id and the two pieces that
          belong to the files domain (§3.1.5, regla dura 16). */}
      <div className="border-border border-t pt-4">
        <TableTasksSection
          tableId={tableId}
          helpAudience="players"
          renderFiles={(files) => <FileList files={files} />}
          renderFilePicker={(onPick) => <FilePicker onPick={onPick} offerPublished />}
        />
      </div>

      <div className="border-border border-t pt-4">
        <MySessionsSection tableId={tableId} />
      </div>
    </div>
  )
}

export { MyTableDetailPage as Component }
