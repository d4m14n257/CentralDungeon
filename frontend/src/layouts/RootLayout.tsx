import { useTranslation } from 'react-i18next'
import { Navigate, Outlet, useLocation } from 'react-router'

import { BackendStatusIndicator } from '@/components/BackendStatusIndicator'
import { DevPanel } from '@/components/dev/DevPanel'
import { paths } from '@/config/paths'
import { useAuth } from '@/providers/AuthProvider'

const PUBLIC_PATHS: string[] = [paths.login, paths.authCallback]

/** Session guard only - context layouts never check role (#103), that is the backend's job. */
export function RootLayout() {
  const { t } = useTranslation('common')
  const { isBootstrapping, isAuthenticated } = useAuth()
  const location = useLocation()

  if (isBootstrapping) {
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

  return (
    <>
      <Outlet />
      <BackendStatusIndicator />
      <DevPanel />
    </>
  )
}
