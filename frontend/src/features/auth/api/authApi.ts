import { api } from '@/api/client'

/** The session calls: renewing the access token and ending the session. */
export const authApi = {
  logout: () => api.post<void>('/api/v1/auth/logout'),
}
