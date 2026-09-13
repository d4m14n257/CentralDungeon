import { api } from '@/api/client'

import type { CompleteOnboardingInput, Profile, User, UserSummary } from '../types'

/** The calls about people: the caller's own profile, the picker search, onboarding, and F2.3's profile screens. */
export const usersApi = {
  me: () => api.get<User>('/api/v1/users/me'),
  completeOnboarding: (input: CompleteOnboardingInput) => api.patch<User, CompleteOnboardingInput>('/api/v1/users/me', input),
  /** `q` speaks the search language of lib/searchQuery.ts; the backend parses it again (#164). */
  search: (query: string, size = 8) => api.getPage<UserSummary>('/api/v1/users/search', { q: query, size }),
  /** `/player/profile` (#248). A separate endpoint from {@link usersApi.me}, not a field of it: this
   *  one carries the aggregate attendance the shell never needs to load. */
  myProfile: () => api.get<Profile>('/api/v1/users/me/profile'),
  /**
   * `/player/users/:id` (#41, #44, #47). 404s for anyone the actor is not entitled to see right
   * now — never a confirmation that the id does or does not exist (#249).
   *
   * @param id the profile to read
   */
  profile: (id: string) => api.get<Profile>(`/api/v1/users/${id}/profile`),
  /**
   * The same search, scoped to one table: who could be made a master of it.
   *
   * A different endpoint and not a parameter of the one above, because they are authorized
   * differently — the directory is an admin's, this one answers to the table's own master (#165).
   */
  searchMasterCandidates: (tableId: string, query: string, size = 8) =>
    api.getPage<UserSummary>(`/api/v1/game-tables/${tableId}/master-candidates`, { q: query, size }),
}
