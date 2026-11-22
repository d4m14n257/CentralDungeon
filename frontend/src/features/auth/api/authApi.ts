import { endSession } from '@/api/client'

/** The session calls: renewing the access token and ending the session. */
export const authApi = {
  logout: endSession,
}
