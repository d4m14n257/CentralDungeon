import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

import type { AttendanceStatus, SessionAttendanceEntry } from '../types'

/** Los cuatro valores, en el orden en que se eligen: lo normal primero, `Unknown` último por ser el que deshace. */
const ATTENDANCE_OPTIONS: readonly AttendanceStatus[] = ['Present', 'Excused', 'Absent', 'Unknown'] as const

interface AttendanceEditorProps {
  /** El padrón que llegó del servidor: una línea por jugador activo, con o sin registro previo. */
  roster: SessionAttendanceEntry[]
  /** Si los controles están bloqueados mientras se guarda. */
  isSaving: boolean
  /** Guarda el padrón entero. Se llama con lo que hay en pantalla, no con lo que cambió. */
  onSave: (attendance: Pick<SessionAttendanceEntry, 'userId' | 'attendance'>[]) => void
}

/**
 * El padrón de una sesión: un selector por jugador, y un solo botón de guardar (#36).
 *
 * **Se guarda entero, no línea por línea.** Un master mira la lista y dice qué pasó; guardar cada
 * selector por separado convertiría una decisión en cinco escrituras y dejaría estados a medio
 * guardar que la pantalla tendría que explicar.
 *
 * Registrar la asistencia **no** marca la sesión como jugada: eso es una acción aparte (#195).
 *
 * @param props.roster   el padrón que devolvió el servidor
 * @param props.isSaving si hay un guardado en curso
 * @param props.onSave   qué hacer con el padrón completo
 */
export function AttendanceEditor({ roster, isSaving, onSave }: AttendanceEditorProps) {
  const { t } = useTranslation('tables')
  // El padrón vive en estado local mientras se edita: es un formulario, no datos de servidor.
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
