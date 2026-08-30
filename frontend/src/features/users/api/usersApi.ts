import { api } from '@/api/client'

import type { CompleteOnboardingInput, User } from '../types'

export const usersApi = {
  me: () => api.get<User>('/api/v1/users/me'),
  completeOnboarding: (input: CompleteOnboardingInput) => api.patch<User, CompleteOnboardingInput>('/api/v1/users/me', input),
}
