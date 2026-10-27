import { useTranslation } from 'react-i18next'
import { Navigate, Outlet, useLocation } from 'react-router'

import { BackendStatusIndicator } from '@/components/BackendStatusIndicator'
import { DevPanel } from '@/components/dev/DevPanel'
import { paths } from '@/config/paths'
import { useMe } from '@/features/users'
import { useAuth } from '@/providers/AuthProvider'

const PUBLIC_PATHS: string[] = [paths.login, paths.authCallback]

/**
 * Session guard. Besides requiring a session, it is where the onboarding blocks (ADR-10 §4): whoever
 * has not completed it is sent to `/onboarding` from any path, and whoever has is sent out of it to
 * the root dispatcher. Which context an account may enter is each context layout's guard (ADR-20 §2);
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
