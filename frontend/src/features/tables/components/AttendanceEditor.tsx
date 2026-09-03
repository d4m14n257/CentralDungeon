import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

import type { AttendanceStatus, SessionAttendanceEntry } from '../types'

/** The four values, in the order they are picked: the usual ones first, `Unknown` last as the one that undoes. */
const ATTENDANCE_OPTIONS: readonly AttendanceStatus[] = ['Present', 'Excused', 'Absent', 'Unknown'] as const

interface AttendanceEditorProps {
  /** The roster the server sent: one line per active player, with or without an earlier record. */
  roster: SessionAttendanceEntry[]
  /** Whether the controls are locked while a save is in flight. */
  isSaving: boolean
  /** Saves the whole roster. It is called with what is on screen, not with what changed. */
  onSave: (attendance: Pick<SessionAttendanceEntry, 'userId' | 'attendance'>[]) => void
}

/**
 * A session's roster: one select per player, and a single save button (#36).
 *
 * **It is saved whole, not line by line.** A master looks at the list and says what happened; saving
 * each select on its own would turn one decision into five writes and leave half-saved states the
 * screen would then have to explain.
 *
 * Recording attendance does **not** mark the session as played: that is a separate action (#195).
 *
 * @param props.roster   the roster the server returned
 * @param props.isSaving whether a save is in flight
 * @param props.onSave   what to do with the complete roster
 */
export function AttendanceEditor({ roster, isSaving, onSave }: AttendanceEditorProps) {
  const { t } = useTranslation('tables')
  // The roster lives in local state while it is edited: it is a form, not server data.
  const [draft, setDraft] = useState<Record<string, AttendanceStatus>>(() =>
    Object.fromEntries(roster.map((line) => [line.userId, line.attendance])),
  )

  if (roster.length === 0) {
    return <p className="text-fg-muted text-sm">{t('sessions.attendance.noPlayers')}</p>
  }

  return (
    <div className="space-y-3">
      <ul className="divide-border divide-y">
        {roster.map((line) => (
          <li key={line.userId} className="flex items-center justify-between gap-4 py-2">
            <span className="truncate text-sm">{line.userName}</span>
            <Select
              value={draft[line.userId] ?? line.attendance}
              onValueChange={(value) => setDraft((current) => ({ ...current, [line.userId]: value as AttendanceStatus }))}
            >
              <SelectTrigger className="w-40" aria-label={t('sessions.attendance.forPlayer', { name: line.userName })}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ATTENDANCE_OPTIONS.map((option) => (
                  <SelectItem key={option} value={option}>
                    {t(`sessions.attendance.${option}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </li>
        ))}
      </ul>
      <div className="flex justify-end">
        <Button
          size="sm"
          disabled={isSaving}
          onClick={() => onSave(roster.map((line) => ({ userId: line.userId, attendance: draft[line.userId] ?? line.attendance })))}
        >
          {t('sessions.attendance.save')}
        </Button>
      </div>
    </div>
  )
}
