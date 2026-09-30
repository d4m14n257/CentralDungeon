import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'

import { AttendanceSummaryView } from '@/components/AttendanceSummaryView'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { ForbiddenState } from '@/components/ForbiddenState'
import { Skeleton } from '@/components/ui/skeleton'
import { playerMyTablesPath, tableDetailPath } from '@/config/paths'
import { HelpButton } from '@/features/help'
import { SectionHeader } from '@/components/SectionHeader'
import { FileList, FilePicker, useCommitStagedFiles } from '@/features/files'
import { SessionList, TableStatusBadge, useGameTable, useMySessions, type MySessions } from '@/features/tables'
import { TableTasksSection } from '@/features/tasks'
import { browserTimeZone, formatSlot, utcSlotToLocal } from '@/lib/date'
import { cn } from '@/lib/utils'
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
        {/* The three numbers are explained in the help, under its stable #ref (#137, #167, #168). */}
        <SectionHeader title={t('sessions.myAttendanceTitle')} help="players.my-sessions" />
        <AttendanceSummaryView summary={mine.summary} />
      </section>

      <section className="space-y-2">
        <h2 className="section-label">{t('sessions.myCalendarTitle')}</h2>
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
 * `/player/my-tables/:id` — my table: its agenda, its sessions and **my** attendance, read-only.
 *
 * It is the minimum player-side screen F1 needs to be testable end to end; the rest of it — tasks,
 * files, the other players — arrives with F1.5 and F2.
 *
 * Everything on it is in the reader's own time; what the server stores is UTC (#22).
 *
 * **The standard header** (#280): the status next to the name instead of in the corner where the
 * main action goes, «Volver a mis mesas» above, «Cómo funciona» on the right, and the link to the
 * public detail as the line under the title. There is no main action: this view only reads.
 */
export function MyTableDetailPage() {
  const commit = useCommitStagedFiles()
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
  // The length rides along: it belongs to the slot now (#228), and utcSlotToLocal only moves the day and the hour.
  const localSchedule = table.schedule.map((slot) => ({ ...utcSlotToLocal(slot, timeZone), duration: slot.duration }))

  return (
    <div className="space-y-4">
      <PageHeader
        title={table.name}
        badge={<TableStatusBadge status={table.status} />}
        back={{ to: playerMyTablesPath(), label: t('detail.backToMyTables') }}
        help="players.my-tables"
        description={
          <Link to={tableDetailPath(table.id)} className="underline">
            {t('sessions.seePublicDetail')}
          </Link>
        }
      />

      <div className="border-border-strong bg-surface space-y-6 rounded-xl border p-6">
        {localSchedule.length > 0 && (
          <section>
            <h2 className="section-label">{t('detail.schedule')}</h2>
            <ul className="mt-1.5 space-y-0.5 text-sm">
              {localSchedule.map((slot) => (
                <li key={`${slot.weekday}-${slot.hourtime}`}>{formatSlot(slot, i18n.language, slot.duration)}</li>
              ))}
            </ul>
            <p className="text-fg-subtle mt-1 text-xs">{t('detail.scheduleTimeZone', { timeZone })}</p>
          </section>
        )}

        {/* What the table shares (#79). Private attachments never arrive here — the server leaves them
          out of the detail, so the screen has nothing to hide. */}
        {/* The divider separates it from the agenda, so it only exists when the agenda does. */}
        <section className={cn(localSchedule.length > 0 && 'border-border border-t pt-4')}>
          <SectionHeader title={tFiles('table.readOnlyTitle')} help="players.files" />
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
        </section>

        {/* What the table asks of its players, and of me in particular (#63, #76). The block owns its
          own query and its own dialog; this screen only hands it the id and the two pieces that
          belong to the files domain (§3.1.5, regla dura 16). */}
        <div className="border-border border-t pt-4">
          <TableTasksSection
            tableId={tableId}
            renderHelp={() => <HelpButton section="players.tasks" size="sm" />}
            renderFiles={(files) => <FileList files={files} />}
            renderFilePicker={(onPick) => <FilePicker onPick={onPick} offerPublished cajon="PlayerSubmission" />}
            // Uploading happens here and not in the picker (#238): this screen owns the send, so it is
            // what turns the staged files into ids right before the answer travels.
            commitFiles={(staged) => commit.mutateAsync({ staged, fileCategory: 'PlayerSubmission' })}
          />
        </div>

        <div className="border-border border-t pt-4">
          <MySessionsSection tableId={tableId} />
        </div>
      </div>
    </div>
  )
}

export { MyTableDetailPage as Component }
