import { Navigate, Outlet, useLocation } from 'react-router'

import { paths } from '@/config/paths'
import { useAuth } from '@/providers/AuthProvider'

const PUBLIC_PATHS: string[] = [paths.login, paths.authCallback]

/** Session guard only - context layouts never check role (#103), that is the backend's job. */
export function RootLayout() {
  const { isBootstrapping, isAuthenticated } = useAuth()
  const location = useLocation()

  if (isBootstrapping) {
    return (
      <div className="flex min-h-svh items-center justify-center">
        <span className="text-muted-foreground text-sm">Cargando...</span>
      </div>
    )
  }

  const isPublicPath = PUBLIC_PATHS.includes(location.pathname)
  if (!isAuthenticated && !isPublicPath) {
    return <Navigate to={paths.login} replace />
  }

  return <Outlet />
}
