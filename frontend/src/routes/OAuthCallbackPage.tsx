import { useTranslation } from 'react-i18next'
import { Navigate, useSearchParams } from 'react-router'

import { Button } from '@/components/ui/button'
import { env } from '@/config/env'
import { paths } from '@/config/paths'
import { useMe } from '@/features/users'
import { useAuth } from '@/providers/AuthProvider'

/**
 * Backend redirect target after the OAuth2 handshake (decisiones.md #38, #125). A full page
 * reload, not an SPA navigation: AuthProvider remounts and picks up the refresh cookie the
 * backend just set - no chicken-and-egg 401 needed to discover the session.
 */
export function OAuthCallbackPage() {
  const { t } = useTranslation('auth')
  const [searchParams] = useSearchParams()
  const { isBootstrapping, isAuthenticated } = useAuth()
  const meQuery = useMe(isAuthenticated)

  const error = searchParams.get('error')
  const inviteUrl = searchParams.get('inviteUrl')

  if (error === 'not_guild_member') {
    return (
      <div className="max-w-sm space-y-4 text-center">
        <h1 className="text-lg font-semibold">{t('callback.notGuildMemberTitle')}</h1>
        <p className="text-muted-foreground text-sm">{t('callback.notGuildMemberDescription')}</p>
        {inviteUrl && (
          <Button asChild className="w-full">
            <a href={inviteUrl} target="_blank" rel="noreferrer">
              {t('callback.joinServer')}
            </a>
          </Button>
        )}
        <Button variant="outline" asChild className="w-full">
          <a href={`${env.apiBaseUrl}/oauth2/authorization/discord`}>{t('callback.retry')}</a>
        </Button>
      </div>
    )
  }

  if (error === 'user_blocked') {
    return (
      <div className="max-w-sm space-y-2 text-center">
        <h1 className="text-lg font-semibold">{t('callback.blockedTitle')}</h1>
        <p className="text-muted-foreground text-sm">{t('callback.blockedDescription')}</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="max-w-sm text-center">
        <h1 className="text-lg font-semibold">{t('callback.genericErrorTitle')}</h1>
      </div>
    )
  }

  if (isBootstrapping || (isAuthenticated && meQuery.isPending)) {
    return <p className="text-muted-foreground text-sm">{t('callback.loading')}</p>
  }

  if (!isAuthenticated) {
    return <Navigate to={paths.login} replace />
  }

  if (meQuery.data?.needsOnboarding) {
    return <Navigate to={paths.onboarding} replace />
  }

  return <Navigate to={paths.home} replace />
}

export { OAuthCallbackPage as Component }
