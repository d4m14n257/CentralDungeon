import { api } from '@/api/client'

import type { CreateRegistrationInput, Registration, TablePlayer } from '../types'

/** The calls about applications: applying, reading the queue, and answering it. */
export const registrationsApi = {
  apply: (tableId: string, input: CreateRegistrationInput) =>
    api.post<Registration, CreateRegistrationInput>(`/api/v1/game-tables/${tableId}/registrations`, input),
  candidates: (tableId: string, page = 0) => api.getPage<Registration>(`/api/v1/game-tables/${tableId}/registrations`, { page }),
  mine: (page = 0) => api.getPage<Registration>('/api/v1/registrations/mine', { page }),
  /**
   * The table's current players — its roster, for the master.
   *
   * A different question from `candidates`, which is the FIFO queue waiting to get in (#28). The
   * first thing that needed it is choosing who a `Single` task is addressed to: offering the whole
   * platform there would be offering people who cannot be chosen.
   */
  players: (tableId: string) => api.get<TablePlayer[]>(`/api/v1/game-tables/${tableId}/players`),
  accept: (registrationId: string) => api.post<Registration>(`/api/v1/registrations/${registrationId}/accept`),
  /** Withdrawing one's own pending application - the way out R4's clash notice needs (#178). */
  withdraw: (registrationId: string) => api.delete(`/api/v1/registrations/${registrationId}`),
  reject: (registrationId: string, justification: string) =>
    api.post<Registration, { justification: string }>(`/api/v1/registrations/${registrationId}/reject`, { justification }),

  /**
   * The veto: the table's `Primary` throwing somebody out of **this** table (#29, #39).
   *
   * **Hung off the table and not off the registration**, unlike `accept` and `reject` above, and that
   * is the shape of the rule rather than a stylistic choice: who may do this is "the `Primary` of
   * *this* table", so the table belongs in the path where the authorization can be checked against it
   * (#121). A `Secondary` calling it gets `403 NOT_PRIMARY_MASTER` — it is who you are, not what you
   * sent — and should be calling {@link requestBlock} instead.
   *
   * @param tableId        the table the veto is for
   * @param registrationId the application being vetoed
   * @param justification  why, required (#39)
   */
  block: (tableId: string, registrationId: string, justification: string) =>
    api.post<Registration, { justification: string }>(`/api/v1/game-tables/${tableId}/registrations/${registrationId}/block`, {
      justification,
    }),

  /**
   * Lifting a veto, which is the half of #39 that makes the other half acceptable.
   *
   * The reason is required here too, and for the same purpose: what is being recorded is not the
   * veto but the **change**, so that a pattern of impulsive vetoes is visible afterwards.
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
   * A co-master **asking** for a veto, which is all a `Secondary` can do (#39, #71).
   *
   * **A second endpoint and not the same one behaving differently.** The screen already knows which
   * of the two the reader is, and the phase document asks it to say so *before* the button is
   * pressed rather than after; one route that sometimes vetoes and sometimes files a request would be
   * a response with two nullable halves, which R3 forbids.
   *
   * **Its answer is deliberately not read** — see {@link UnreadBody}. What comes back is an
   * `ApprovalRequestDetailResponse`, and that is `features/approvals`' vocabulary — a feature never
   * imports from another (regla dura 16). The screen re-reads the table's pending veto requests
   * instead, which is the list it actually renders.
   *
   * @param tableId        the table
   * @param registrationId the application the co-master wants vetoed
   * @param justification  why, required — it is what the `Primary` reads to decide
   */
  requestBlock: (tableId: string, registrationId: string, justification: string) =>
    api.post<void, { justification: string }>(`/api/v1/game-tables/${tableId}/registrations/${registrationId}/request-block`, {
      justification,
    }),
}
