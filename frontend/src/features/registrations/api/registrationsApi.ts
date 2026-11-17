import { api } from '@/api/client'

import type { CreateRegistrationInput, Registration, TablePlayer } from '../types'

/**
 * A body this feature deliberately does not read: `request-block` answers with an
 * `ApprovalRequestDetailResponse`, which is `features/approvals`' vocabulary and a feature never
 * imports from another. `unknown` and not `void`, because `void` would claim the wire is empty when
 * it is not; callers re-read the list they render instead.
 */
type UnreadBody = unknown

/** The calls about applications: applying, reading the queue, and answering it. */
export const registrationsApi = {
  apply: (tableId: string, input: CreateRegistrationInput) =>
    api.post<Registration, CreateRegistrationInput>(`/api/v1/game-tables/${tableId}/registrations`, input),
  candidates: (tableId: string, page = 0) => api.getPage<Registration>(`/api/v1/game-tables/${tableId}/registrations`, { page }),
  mine: (page = 0) => api.getPage<Registration>('/api/v1/registrations/mine', { page }),
  /**
   * The table's current players — its roster, for the master.
   *
   * A different question from `candidates`, which is the FIFO queue waiting to get in (ADR-09 §1). The
   * first thing that needed it is choosing who a `Single` task is addressed to: offering the whole
   * platform there would be offering people who cannot be chosen.
   */
  players: (tableId: string) => api.get<TablePlayer[]>(`/api/v1/game-tables/${tableId}/players`),
  accept: (registrationId: string) => api.post<Registration>(`/api/v1/registrations/${registrationId}/accept`),
  /** Withdrawing one's own pending application - the way out R4's clash notice needs (ADR-08 §2). */
  withdraw: (registrationId: string) => api.delete(`/api/v1/registrations/${registrationId}`),
  reject: (registrationId: string, justification: string) =>
    api.post<Registration, { justification: string }>(`/api/v1/registrations/${registrationId}/reject`, { justification }),

  /**
   * Lifting a veto, which is what makes a veto acceptable: it is reversible.
   *
   * Hung off the table and not off the registration, because who may do it is "the `Primary` of
   * *this* table, or an admin", and the table belongs in the path where that can be checked. A
   * `Secondary` gets `403 NOT_PRIMARY_MASTER`.
   *
   * The reason is required: what is being recorded is not the veto but the **change**, so that a
   * pattern of impulsive vetoes is visible afterwards.
   *
   * @param tableId        the table
   * @param registrationId the application whose veto is being lifted
   * @param justification  why, required
   */
  unblock: (tableId: string, registrationId: string, justification: string) =>
    api.post<Registration, { justification: string }>(`/api/v1/game-tables/${tableId}/registrations/${registrationId}/unblock`, {
      justification,
    }),

  /**
   * A master of the table **asking** the admins for a veto: nobody vetoes from here.
   *
   * Nothing changes for the person while the request waits. **Its answer is deliberately not read**
   * — see {@link UnreadBody}: what comes back is an `ApprovalRequestDetailResponse`, and that is
   * `features/approvals`' vocabulary — a feature never imports from another (regla dura 16). The
   * screen re-reads the table's pending veto requests instead, which is the list it renders.
   *
   * @param tableId        the table
   * @param registrationId the application the master wants vetoed
   * @param justification  why, required — it is what the admin reads to decide
   */
  requestBlock: (tableId: string, registrationId: string, justification: string) =>
    api.post<UnreadBody, { justification: string }>(`/api/v1/game-tables/${tableId}/registrations/${registrationId}/request-block`, {
      justification,
    }),
}
