import { CalendarClock, CircleCheck, CircleX } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useOutletContext } from 'react-router'
import { toast } from 'sonner'

import { CollapsibleSection } from '@/components/CollapsibleSection'
import { useConfirm } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { IconAction } from '@/components/IconAction'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { helpPath } from '@/config/paths'
import {
  AttendanceEditor,
  SessionStatusBadge,
  useCancelSession,
  useHoldSession,
  useRecordAttendance,
  useTableSessions,
  useUpdateSession,
  type GameTableStatus,
  type TableSession,
} from '@/features/tables'
import { browserTimeZone, formatDateTime, localInputToUtcIso, utcIsoToLocalInput } from '@/lib/date'

interface OutletContext {
  tableId: string
  status: GameTableStatus
}

/**
 * One session and everything that can be done to it: correct its date, write notes, mark it played,
 * call it off, and record who came.
 *
 * It is collapsed by default: a calendar of twelve open sessions is a screen nobody can read, and
 * the header already says the only thing that gets read at a glance — when it is and how it went.
 */
function SessionRow({ session, tableId }: { session: TableSession; tableId: string }) {
  const { t, i18n } = useTranslation('master')
  const timeZone = browserTimeZone()
  const confirm = useConfirm()

  const updateSession = useUpdateSession(tableId)
  const holdSession = useHoldSession(tableId)
  const cancelSession = useCancelSession(tableId)
  const recordAttendance = useRecordAttendance(tableId)

  const [scheduledAt, setScheduledAt] = useState(() => utcIsoToLocalInput(session.scheduledAt, timeZone))
  const [notes, setNotes] = useState(session.notes ?? '')
  const isScheduled = session.status === 'Scheduled'

  function handleSaveDetails() {
    updateSession.mutate(
      { sessionId: session.id, request: { scheduledAt: localInputToUtcIso(scheduledAt, timeZone), notes: notes || null } },
      { onSuccess: () => toast.success(t('sessions.updateSuccess')) },
    )
  }

  async function handleHold() {
    const confirmed = await confirm({ title: t('sessions.holdConfirmTitle'), description: t('sessions.holdConfirmDescription') })
    if (!confirmed) return
    holdSession.mutate(session.id, { onSuccess: () => toast.success(t('sessions.holdSuccess')) })
  }

  async function handleCancel() {
    // The replacement of #194 is announced **before** confirming, not after: it is half of what
    // cancelling does, and finding out by spotting a new session would be a surprise.
    const confirmed = await confirm({
      title: t('sessions.cancelConfirmTitle', { number: session.sequenceNumber }),
      description: t('sessions.cancelConfirmDescription'),
    })
    if (!confirmed) return
    cancelSession.mutate(session.id, { onSuccess: () => toast.success(t('sessions.cancelSuccess')) })
  }

  return (
    <CollapsibleSection
      title={t('sessions.number', { number: session.sequenceNumber, ns: 'tables' })}
      summary={formatDateTime(session.scheduledAt, i18n.language, timeZone)}
      actions={
        <>
          <SessionStatusBadge status={session.status} />
          {isScheduled && (
            <>
              <IconAction
                label={t('sessions.hold')}
                icon={<CircleCheck className="size-4" />}
                disabled={holdSession.isPending}
                onClick={() => void handleHold()}
              />
              <IconAction
                label={t('sessions.cancel')}
                icon={<CircleX className="size-4" />}
                disabled={cancelSession.isPending}
                onClick={() => void handleCancel()}
              />
            </>
          )}
        </>
      }
    >
      <div className="space-y-4">
        <div className="space-y-2">
          <label htmlFor={`scheduled-${session.id}`} className="text-sm font-medium">
            {t('sessions.dateLabel')}
          </label>
          <Input
            id={`scheduled-${session.id}`}
            type="datetime-local"
            value={scheduledAt}
            disabled={!isScheduled}
            onChange={(event) => setScheduledAt(event.target.value)}
          />
          {/* The date is stored in UTC (#22); this says which zone it is being typed in. */}
          <p className="text-fg-subtle text-xs">{t('sessions.timeZone', { timeZone, ns: 'tables' })}</p>
        </div>

        <div className="space-y-2">
          <label htmlFor={`notes-${session.id}`} className="text-sm font-medium">
            {t('sessions.notesLabel')}
          </label>
          <Textarea
            id={`notes-${session.id}`}
            value={notes}
            disabled={!isScheduled}
            onChange={(event) => setNotes(event.target.value)}
            placeholder={t('sessions.notesPlaceholder')}
          />
          <p className="text-fg-subtle text-xs">{t('sessions.notesArePrivate')}</p>
        </div>

        {isScheduled ? (
          <div className="flex justify-end">
            <Button size="sm" variant="outline" disabled={updateSession.isPending} onClick={handleSaveDetails}>
              {t('sessions.saveDetails')}
            </Button>
          </div>
        ) : (
          // Principio 2: a greyed-out control that does not say why is worse than no control at all.
          <p className="text-fg-muted text-xs">{t(`sessions.locked.${session.status}`)}</p>
        )}

        <div className="border-border space-y-2 border-t pt-4">
          <h3 className="text-sm font-medium">{t('sessions.attendanceTitle')}</h3>
          {session.status === 'Cancelled' ? (
            <p className="text-fg-muted text-sm">{t('sessions.attendanceNotForCancelled')}</p>
          ) : (
            <AttendanceEditor
              roster={session.attendance}
              isSaving={recordAttendance.isPending}
              onSave={(attendance) =>
                recordAttendance.mutate(
                  { sessionId: session.id, request: { attendance } },
                  { onSuccess: () => toast.success(t('sessions.attendanceSuccess')) },
                )
              }
            />
          )}
        </div>
      </div>
    </CollapsibleSection>
  )
}

function SessionsPanel({ tableId, status }: OutletContext) {
  const { t } = useTranslation('master')
  // isLoadingError, not isError: see docs/decisiones.md #150.
  const { data, isPending, isLoadingError, refetch } = useTableSessions(tableId)

  if (isPending) {
    return <Skeleton className="h-40 w-full" />
  }

  if (isLoadingError) {
    return <ErrorState onRetry={() => void refetch()} />
  }

  // A paused table promises no dates, so the backend does not send the pending ones (#32, #33). The
  // empty state has to say that, and not look like something broke.
  if (data.length === 0) {
    return (
      <EmptyState
        title={status === 'Pause' ? t('sessions.pausedTitle') : t('sessions.emptyTitle')}
        description={status === 'Pause' ? t('sessions.pausedDescription') : t('sessions.emptyDescription')}
      />
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <CalendarClock aria-hidden="true" className="text-fg-muted size-4" />
          <h2 className="text-sm font-medium">{t('sessions.title', { count: data.length })}</h2>
        </div>
        <Link to={helpPath('masters', 'sessions')} className="text-fg-muted text-xs underline">
          {t('sessions.help')}
        </Link>
      </div>
      {status === 'Pause' && <p className="text-fg-muted text-sm">{t('sessions.pausedDescription')}</p>}
      <div className="space-y-2">
        {data.map((session) => (
          <SessionRow key={session.id} session={session} tableId={tableId} />
        ))}
      </div>
    </div>
  )
}

/**
 * The Sessions tab of `/master/tables/:id`: the materialized calendar and the four things a master
 * does with it — correct a date, write notes, mark it played (#195), and cancel, which adds a
 * replacement session at the end (#194).
 *
 * Dates are typed and read in the viewer's own time; what travels is UTC (#22).
 */
export function MasterTableSessionsTab() {
  const context = useOutletContext<OutletContext>()
  return <SessionsPanel {...context} />
}

export { MasterTableSessionsTab as Component }
