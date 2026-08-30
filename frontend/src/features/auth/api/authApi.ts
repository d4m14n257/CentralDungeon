import { api } from '@/api/client'

export const authApi = {
  logout: () => api.post<void>('/api/v1/auth/logout'),
}
