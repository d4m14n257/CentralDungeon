import { api } from '@/api/client'

import type { CompleteOnboardingInput, User, UserSummary } from '../types'

export const usersApi = {
  me: () => api.get<User>('/api/v1/users/me'),
  completeOnboarding: (input: CompleteOnboardingInput) => api.patch<User, CompleteOnboardingInput>('/api/v1/users/me', input),
  /** `q` habla el lenguaje de búsqueda de lib/searchQuery.ts; el backend lo vuelve a parsear (#164). */
  search: (query: string, size = 8) => api.getPage<UserSummary>('/api/v1/users/search', { q: query, size }),
}
