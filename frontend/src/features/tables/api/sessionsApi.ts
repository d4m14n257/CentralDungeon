import { api } from '@/api/client'

import type { MySessions, RecordAttendanceRequest, TableSession, UpdateSessionRequest } from '../types'

/**
 * Las llamadas sobre las sesiones de una mesa.
 *
 * Objeto aparte de `gameTablesApi` porque el corte de la API también lo es: el calendario cuelga de
 * la mesa (`/game-tables/{id}/sessions`), pero actuar sobre una sesión la direcciona a ella misma
 * (`/sessions/{id}/hold`) — el mismo criterio que las postulaciones.
 *
 * Ninguna listada es paginada: el calendario está acotado por `totalSessions` y se lee entero.
 */
export const sessionsApi = {
  /** El calendario como lo ve quien dirige la mesa, con notas y padrón. */
  forTable: (tableId: string) => api.get<TableSession[]>(`/api/v1/game-tables/${tableId}/sessions`),
  /** El calendario del jugador y su asistencia, para `/my/tables/:id`. */
  mine: (tableId: string) => api.get<MySessions>(`/api/v1/game-tables/${tableId}/sessions/mine`),
  /** Corregir fecha y notas. Reemplaza los dos campos, no parchea (#189). */
  update: (sessionId: string, request: UpdateSessionRequest) =>
    api.patch<TableSession, UpdateSessionRequest>(`/api/v1/sessions/${sessionId}`, request),
  /** Marcar la sesión como jugada. Acción propia, separada del registro de asistencia (#195). */
  hold: (sessionId: string) => api.post<TableSession>(`/api/v1/sessions/${sessionId}/hold`),
  /** Cancelar una sesión. Devuelve el calendario entero: la cancelación y su reposición son un solo cambio (#194). */
  cancel: (sessionId: string) => api.post<TableSession[]>(`/api/v1/sessions/${sessionId}/cancel`),
  /** Registrar quién vino. El padrón viaja entero, por eso es `PUT` y no `PATCH`. */
  recordAttendance: (sessionId: string, request: RecordAttendanceRequest) =>
    api.put<TableSession, RecordAttendanceRequest>(`/api/v1/sessions/${sessionId}/attendance`, request),
}
