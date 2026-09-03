import { useMutation } from '@tanstack/react-query'

import { authApi } from './authApi'

/**
 * Ends the session: clears the refresh cookie on the server, drops the in-memory access token, and
 * empties the query cache so the next person to log in never sees the previous one's data.
 *
 * @returns the logout mutation
 */
export function useLogout() {
  return useMutation({ mutationFn: authApi.logout })
}
