import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, Outlet, useLocation } from 'react-router'

import { BackendStatusIndicator } from '@/components/BackendStatusIndicator'
import { DevPanel } from '@/components/dev/DevPanel'
import { paths } from '@/config/paths'
import { useMe } from '@/features/users'
import { rememberDestination } from '@/lib/postLoginDestination'
import { useAuth } from '@/providers/AuthProvider'

const PUBLIC_PATHS: string[] = [paths.login, paths.authCallback]

/** Paths that are never worth coming back to after a login: the dispatcher and the first-run step. */
const NOT_A_DESTINATION: string[] = [paths.root, paths.onboarding]

/**
 * Session guard. Besides requiring a session, it is where the onboarding blocks (ADR-10 §4): whoever
 * has not completed it is sent to `/onboarding` from any path, and whoever has is sent out of it to
 * the root dispatcher. Whoever has a session is also sent away from `/login`, to the root dispatcher.
 * A visitor without one who followed a deep link is sent to `/login` and the link is remembered, so the
 * dispatcher can take them there once they are in (`lib/postLoginDestination.ts`). Which context an account may enter is each context layout's guard (ADR-20 §2);
 * authorization stays the backend's (ADR-20 §1).
 *
 * If `/users/me` fails the guard lets the request through: each screen shows its own error, and a
 * failed read is not proof that onboarding is pending.
 */
export function RootLayout() {
  const { t } = useTranslation('common')
  const { isBootstrapping, isAuthenticated } = useAuth()
  const location = useLocation()
  const me = useMe(isAuthenticated)
  const hadSession = useRef(false)
  const { pathname, search, hash } = location

  // Only a visit that started without a session remembers where it was going: whoever just signed out
  // is bounced to /login by this same guard, and must not leave their last page for the next login.
  useEffect(() => {
    if (isBootstrapping) return
    if (isAuthenticated) {
      hadSession.current = true
    } else if (!hadSession.current && !PUBLIC_PATHS.includes(pathname) && !NOT_A_DESTINATION.includes(pathname)) {
      rememberDestination(pathname + search + hash)
    }
  }, [isBootstrapping, isAuthenticated, pathname, search, hash])

  if (isBootstrapping || (isAuthenticated && me.isPending)) {
    return (
      <>
        <div className="flex min-h-svh items-center justify-center">
          <span className="text-fg-muted text-sm">{t('states.loading')}</span>
        </div>
        <BackendStatusIndicator />
      </>
    )
  }

  if (isAuthenticated && location.pathname === paths.login) {
    return <Navigate to={paths.root} replace />
  }

  const isPublicPath = PUBLIC_PATHS.includes(location.pathname)
  if (!isAuthenticated && !isPublicPath) {
    return <Navigate to={paths.login} replace />
  }

  if (me.data) {
    const onOnboarding = location.pathname === paths.onboarding
    if (me.data.needsOnboarding && !onOnboarding) return <Navigate to={paths.onboarding} replace />
    if (!me.data.needsOnboarding && onOnboarding) return <Navigate to={paths.root} replace />
  }

  return (
    <>
      <Outlet />
      <BackendStatusIndicator />
      <DevPanel />
    </>
  )
}
