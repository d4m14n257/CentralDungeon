import { api } from '@/api/client'

import type { CreateRegistrationInput, Registration } from '../types'

/** The calls about applications: applying, reading the queue, and answering it. */
export const registrationsApi = {
  apply: (tableId: string, input: CreateRegistrationInput) =>
    api.post<Registration, CreateRegistrationInput>(`/api/v1/game-tables/${tableId}/registrations`, input),
  candidates: (tableId: string, page = 0) => api.getPage<Registration>(`/api/v1/game-tables/${tableId}/registrations`, { page }),
  mine: (page = 0) => api.getPage<Registration>('/api/v1/registrations/mine', { page }),
  accept: (registrationId: string) => api.post<Registration>(`/api/v1/registrations/${registrationId}/accept`),
  /** Withdrawing one's own pending application - the way out R4's clash notice needs (#178). */
  withdraw: (registrationId: string) => api.delete(`/api/v1/registrations/${registrationId}`),
  reject: (registrationId: string, justification: string) =>
    api.post<Registration, { justification: string }>(`/api/v1/registrations/${registrationId}/reject`, { justification }),
}
