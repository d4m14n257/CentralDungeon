import { api } from '@/api/client'

import type { CompleteOnboardingInput, User, UserSummary } from '../types'

/** The calls about people: the caller's own profile, the picker search, and onboarding. */
export const usersApi = {
  me: () => api.get<User>('/api/v1/users/me'),
  completeOnboarding: (input: CompleteOnboardingInput) => api.patch<User, CompleteOnboardingInput>('/api/v1/users/me', input),
  /** `q` speaks the search language of lib/searchQuery.ts; the backend parses it again (#164). */
  search: (query: string, size = 8) => api.getPage<UserSummary>('/api/v1/users/search', { q: query, size }),
  /**
   * The same search, scoped to one table: who could be made a master of it.
   *
   * A different endpoint and not a parameter of the one above, because they are authorized
   * differently — the directory is an admin's, this one answers to the table's own master (#165).
   */
  searchMasterCandidates: (tableId: string, query: string, size = 8) =>
    api.getPage<UserSummary>(`/api/v1/game-tables/${tableId}/master-candidates`, { q: query, size }),
}
