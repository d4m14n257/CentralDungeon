import { api } from '@/api/client'

import type { MySessions, RecordAttendanceRequest, TableSession, UpdateSessionRequest } from '../types'

/**
 * The calls about a table's sessions.
 *
 * Its own object, separate from `gameTablesApi`, because the API's own split is the same one: the
 * calendar hangs off the table (`/game-tables/{id}/sessions`), while acting on one session addresses
 * the session itself (`/sessions/{id}/hold`) — the same criterion the registrations use.
 *
 * None of the listings is paginated: the calendar is bounded by `totalSessions` and read whole.
 */
export const sessionsApi = {
  /** The calendar as the people running the table see it, with notes and roster. */
  forTable: (tableId: string) => api.get<TableSession[]>(`/api/v1/game-tables/${tableId}/sessions`),
  /** The player's own calendar and their attendance, for `/my/tables/:id`. */
  mine: (tableId: string) => api.get<MySessions>(`/api/v1/game-tables/${tableId}/sessions/mine`),
  /** Correcting the date and the notes. It replaces both fields rather than patching them (#189). */
  update: (sessionId: string, request: UpdateSessionRequest) =>
    api.patch<TableSession, UpdateSessionRequest>(`/api/v1/sessions/${sessionId}`, request),
  /** Marking the session as played. Its own action, separate from recording attendance (#195). */
  hold: (sessionId: string) => api.post<TableSession>(`/api/v1/sessions/${sessionId}/hold`),
  /** Cancelling a session. Answers with the whole calendar: the cancellation and its replacement are one change (#194). */
  cancel: (sessionId: string) => api.post<TableSession[]>(`/api/v1/sessions/${sessionId}/cancel`),
  /** Recording who came. The roster travels as a whole, which is why this is a `PUT` and not a `PATCH`. */
  recordAttendance: (sessionId: string, request: RecordAttendanceRequest) =>
    api.put<TableSession, RecordAttendanceRequest>(`/api/v1/sessions/${sessionId}/attendance`, request),
}
