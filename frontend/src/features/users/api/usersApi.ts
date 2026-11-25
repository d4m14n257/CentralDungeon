import { api } from '@/api/client'

import type { CompleteOnboardingInput, FinishedTable, Profile, User, UserSummary, UserTable } from '../types'

/** The calls about people: the caller's own profile, the picker search, onboarding, and the profile screens. */
export const usersApi = {
  me: () => api.get<User>('/api/v1/users/me'),
  completeOnboarding: (input: CompleteOnboardingInput) => api.post<User, CompleteOnboardingInput>('/api/v1/users/me/onboarding', input),
  /** The later edit of the same two fields; refused while onboarding is still pending. */
  updateProfile: (input: CompleteOnboardingInput) => api.patch<User, CompleteOnboardingInput>('/api/v1/users/me', input),
  /** `q` speaks the search language of lib/searchQuery.ts; the backend parses it again. */
  search: (query: string, size = 8) => api.getPage<UserSummary>('/api/v1/users/search', { q: query, size }),
  /** `/profile`. A separate endpoint from {@link usersApi.me}, not a field of it: this
   *  one carries the aggregate attendance the shell never needs to load. */
  myProfile: () => api.get<Profile>('/api/v1/users/me/profile'),
  /**
   * `/users/:id`. 404s for anyone the actor is not entitled to see right
   * now — never a confirmation that the id does or does not exist.
   *
   * @param id the profile to read
   */
  profile: (id: string) => api.get<Profile>(`/api/v1/users/${id}/profile`),
  /**
   * The tables behind a profile's finished-tables count, newest closing first. Readable by exactly
   * whoever may read that profile, so it 404s for anyone else just like {@link usersApi.profile}.
   *
   * @param id   whose finished tables
   * @param page zero-based page
   */
  finishedTables: (id: string, page = 0) => api.getPage<FinishedTable>(`/api/v1/users/${id}/finished-tables`, { page, size: 10 }),
  /**
   * Every table a person runs, plays at or applied to - admins only, for their record on
   * /admin/users/:id. Newest table first; paged like every list.
   *
   * @param id   whose tables
   * @param page zero-based page
   */
  tables: (id: string, page = 0) => api.getPage<UserTable>(`/api/v1/users/${id}/tables`, { page, size: 10 }),
  /**
   * The same search, scoped to one table: who could be made a master of it.
   *
   * A different endpoint and not a parameter of the one above, because they are authorized
   * differently — the directory is an admin's, this one answers to the table's own master.
   */
  searchMasterCandidates: (tableId: string, query: string, size = 8) =>
    api.getPage<UserSummary>(`/api/v1/game-tables/${tableId}/master-candidates`, { q: query, size }),
}
