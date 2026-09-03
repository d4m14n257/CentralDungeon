import { useTranslation } from 'react-i18next'

import { browserTimeZone, formatDateTime } from '@/lib/date'

import { SessionStatusBadge } from './SessionStatusBadge'
import type { AttendanceStatus, TableSession } from '../types'

/**
 * The least a session needs to be listed, derived from the base type with `Pick` (regla dura 6).
 * `myAttendance` is optional because only the player's view carries it.
 */
export type SessionListItem = Pick<TableSession, 'id' | 'sequenceNumber' | 'scheduledAt' | 'status'> & {
  myAttendance?: AttendanceStatus
}

interface SessionListProps {
  /** The sessions to show, already in the order they are played. */
  sessions: SessionListItem[]
  /**
   * The zone to show them in. A parameter and not a constant (#111, #192): a caller can pass the
   * one from the profile, and `browserTimeZone()` is only the default — somebody travelling has not
   * moved their table.
   */
  timeZone?: string
}

/**
 * A table's calendar, read-only — what `/tables/:id` and `/my/tables/:id` show.
 *
 * **The dates are shown in the reader's own time** (#22). What arrives from the server is UTC and
 * the conversion happens once, here, through `lib/date.ts`. The footer names the zone it is showing,
 * because a time with no zone is a date everybody reads differently.
 *
 * @param props.sessions the sessions to list
 * @param props.timeZone the zone to show them in; the browser's by default
 */
export function SessionList({ sessions, timeZone = browserTimeZone() }: SessionListProps) {
  const { t, i18n } = useTranslation('tables')

  return (
    <div className="space-y-2">
      <ol className="divide-border divide-y rounded-lg border">
        {sessions.map((session) => (
          <li key={session.id} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 px-4 py-3">
            <div className="min-w-0">
              <p className="text-sm font-medium">{t('sessions.number', { number: session.sequenceNumber })}</p>
              <p className="text-fg-muted text-sm">{formatDateTime(session.scheduledAt, i18n.language, timeZone)}</p>
            </div>
            <div className="flex items-center gap-2">
              {session.myAttendance && session.myAttendance !== 'Unknown' && (
                <span className="text-fg-muted text-xs">{t(`sessions.attendance.${session.myAttendance}`)}</span>
              )}
              <SessionStatusBadge status={session.status} />
            </div>
          </li>
        ))}
      </ol>
      <p className="text-fg-subtle text-xs">{t('sessions.timeZone', { timeZone })}</p>
    </div>
  )
}
