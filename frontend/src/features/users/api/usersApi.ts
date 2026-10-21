import { api } from '@/api/client'

import type { CompleteOnboardingInput, Profile, User, UserSummary, UserTable } from '../types'

/** The calls about people: the caller's own profile, the picker search, onboarding, and F2.3's profile screens. */
export const usersApi = {
  me: () => api.get<User>('/api/v1/users/me'),
  completeOnboarding: (input: CompleteOnboardingInput) => api.patch<User, CompleteOnboardingInput>('/api/v1/users/me', input),
  /** `q` speaks the search language of lib/searchQuery.ts; the backend parses it again (ADR-22 §1). */
  search: (query: string, size = 8) => api.getPage<UserSummary>('/api/v1/users/search', { q: query, size }),
  /** `/player/profile` (ADR-10 §5). A separate endpoint from {@link usersApi.me}, not a field of it: this
   *  one carries the aggregate attendance the shell never needs to load. */
  myProfile: () => api.get<Profile>('/api/v1/users/me/profile'),
  /**
   * `/player/users/:id` (ADR-10 §1, §2). 404s for anyone the actor is not entitled to see right
   * now — never a confirmation that the id does or does not exist (ADR-10 §3).
   *
   * @param id the profile to read
   */
  profile: (id: string) => api.get<Profile>(`/api/v1/users/${id}/profile`),
  /**
   * Every table a person runs, plays at or applied to - admins only (ADR-06 §8), for their record on
   * /admin/users/:id. Newest table first; paged like every list (ADR-04 §6).
   *
   * @param id   whose tables
   * @param page zero-based page
   */
  tables: (id: string, page = 0) => api.getPage<UserTable>(`/api/v1/users/${id}/tables`, { page, size: 10 }),
  /**
   * The same search, scoped to one table: who could be made a master of it.
   *
   * A different endpoint and not a parameter of the one above, because they are authorized
   * differently — the directory is an admin's, this one answers to the table's own master (ADR-07 §8).
   */
  searchMasterCandidates: (tableId: string, query: string, size = 8) =>
    api.getPage<UserSummary>(`/api/v1/game-tables/${tableId}/master-candidates`, { q: query, size }),
}
