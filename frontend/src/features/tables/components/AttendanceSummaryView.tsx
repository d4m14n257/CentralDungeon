import { useTranslation } from 'react-i18next'

import type { AttendanceSummary } from '../types'

interface AttendanceSummaryViewProps {
  /** The three counts and their denominator, exactly as the server returns them. */
  summary: AttendanceSummary
}

/**
 * Somebody's historical attendance on a table (#137).
 *
 * **Three numbers and never a percentage.** A ratio would have decided for the reader, and the
 * distinction it hides is the one that matters: missing after warning and simply not turning up are
 * different facts, and collapsing them makes attendance unexplainable — it is #98 applied one level
 * down. The reader draws a better conclusion from the three numbers than from an average that
 * already drew it for them.
 *
 * The denominator is the sessions **with something recorded**: a table of twelve that started
 * yesterday has eleven unrecorded ones, and counting those would make everybody read as a chronic
 * absentee.
 *
 * @param props.summary the attendance to show
 */
export function AttendanceSummaryView({ summary }: AttendanceSummaryViewProps) {
  const { t } = useTranslation('tables')

  if (summary.registered === 0) {
    return <p className="text-fg-muted text-sm">{t('sessions.attendance.nothingRecorded')}</p>
  }

  return (
    <dl className="flex flex-wrap gap-x-6 gap-y-2 text-sm">
      <div>
        <dt className="text-fg-subtle text-xs">{t('sessions.attendance.Present')}</dt>
        <dd className="font-medium">{summary.present}</dd>
      </div>
      <div>
        <dt className="text-fg-subtle text-xs">{t('sessions.attendance.Excused')}</dt>
        <dd className="font-medium">{summary.excused}</dd>
      </div>
      <div>
        <dt className="text-fg-subtle text-xs">{t('sessions.attendance.Absent')}</dt>
        <dd className="font-medium">{summary.absent}</dd>
      </div>
      <div>
        <dt className="text-fg-subtle text-xs">{t('sessions.attendance.registered')}</dt>
        <dd className="font-medium">{summary.registered}</dd>
      </div>
    </dl>
  )
}
