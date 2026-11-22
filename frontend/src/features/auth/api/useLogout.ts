import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { authApi } from './authApi'

/** Pause before the single retry, long enough for a blip to pass and short enough for somebody waiting to leave. */
const RETRY_DELAY_MS = 500

/**
 * Ends the session on the server: revokes it and clears the refresh cookie. A failed call is retried
 * once; if it fails again the person is told, because on a shared computer a cookie the server never
 * revoked keeps the session alive. The caller still clears the local state in `onSettled`, so a dead
 * backend cannot trap somebody in a session they want to leave.
 *
 * @returns the logout mutation
 */
export function useLogout() {
  const { t } = useTranslation('common')
  return useMutation({
    mutationFn: authApi.logout,
    retry: 1,
    retryDelay: RETRY_DELAY_MS,
    // The notice below is the whole answer; the generic mutation toast would say the same thing worse.
    meta: { showsItsOwnError: true },
    onError: () => toast.error(t('errors.logoutFailed')),
  })
}
