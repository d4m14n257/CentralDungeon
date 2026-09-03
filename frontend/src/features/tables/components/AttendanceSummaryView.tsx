import { useTranslation } from 'react-i18next'

import type { AttendanceSummary } from '../types'

interface AttendanceSummaryViewProps {
  /** Los tres números y su denominador, como los devuelve el servidor. */
  summary: AttendanceSummary
}

/**
 * La asistencia histórica de alguien en una mesa (#137).
 *
 * **Tres números y nunca un porcentaje.** Una razón escondería justo lo que importa: faltar
 * avisando y no aparecer son hechos distintos, y colapsarlos vuelve la asistencia inexplicable —
 * es #98 aplicado un nivel más abajo. Quien lee saca su conclusión mejor con los tres números que
 * con un promedio que ya decidió por él.
 *
 * El denominador son las sesiones **con algo registrado**: una mesa de doce que arrancó ayer tiene
 * once sin registrar, y contarlas haría ver a todos como ausentes crónicos.
 *
 * @param props.summary la asistencia a mostrar
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
