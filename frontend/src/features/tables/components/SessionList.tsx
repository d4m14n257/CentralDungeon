import { useTranslation } from 'react-i18next'

import { browserTimeZone, formatDateTime } from '@/lib/date'

import { SessionStatusBadge } from './SessionStatusBadge'
import type { AttendanceStatus, TableSession } from '../types'

/**
 * Lo mínimo que hace falta para listar una sesión, derivado del tipo base con `Pick` (regla dura 6).
 * `myAttendance` es opcional porque solo la vista del jugador la tiene.
 */
export type SessionListItem = Pick<TableSession, 'id' | 'sequenceNumber' | 'scheduledAt' | 'status'> & {
  myAttendance?: AttendanceStatus
}

interface SessionListProps {
  /** Las sesiones a mostrar, ya en el orden en que se juegan. */
  sessions: SessionListItem[]
  /**
   * La zona en la que mostrarlas. Parámetro y no constante (#111, #192): quien la use puede pasar
   * la del perfil, y `browserTimeZone()` es solo el valor por defecto — alguien de viaje no movió
   * su mesa.
   */
  timeZone?: string
}

/**
 * El calendario de una mesa, de solo lectura — lo que se ve en `/tables/:id` y en `/my/tables/:id`.
 *
 * **Las fechas se muestran en la hora de quien lee** (#22). Lo que llega del servidor es UTC y la
 * conversión pasa una sola vez, acá, con `lib/date.ts`. El pie dice en qué zona se está mostrando,
 * porque un horario sin zona es una fecha que cada quien interpreta distinto.
 *
 * @param props.sessions las sesiones a listar
 * @param props.timeZone la zona en la que mostrarlas; por defecto, la del navegador
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
