import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Navigate, useSearchParams } from 'react-router'

import { Button } from '@/components/ui/button'
import { env } from '@/config/env'
import { paths } from '@/config/paths'
import { cn } from '@/lib/utils'
import { useMe } from '@/features/users'
import { BrandMark } from '@/layouts/components/BrandMark'
import { useAuth } from '@/providers/AuthProvider'

/**
 * The five states of the OAuth return (design/out/screen-auth-callback.html). The verifying one
 * stays a plain title; the other four carry the title inside a toned chip that replaces the heading -
 * so each reads as what it is (a step that is waiting, a step that broke) instead of a grey heading
 * repeated four times. The wordmark at the top is the only thing this screen shares with /login -
 * with no header, it is the only sign you are still in CentralDungeon.
 */
function CallbackCard({ tone, title, children }: { tone?: 'pending' | 'canceled'; title: string; children?: ReactNode }) {
  return (
    <div className="border-border-strong bg-surface w-full max-w-sm rounded-xl border p-6 text-center">
      <BrandMark className="mb-3.5 block font-serif text-base font-bold" />
      {tone ? (
        <span
          className={cn(
            'inline-block rounded-md px-3 py-1.5 text-sm font-medium',
            tone === 'pending' ? 'bg-state-pending-bg text-state-pending-fg' : 'bg-state-canceled-bg text-state-canceled-fg',
          )}
        >
          {title}
        </span>
      ) : (
        <h1 className="font-serif text-lg font-semibold">{title}</h1>
      )}
      {children && <div className="mt-4 space-y-3">{children}</div>}
    </div>
  )
}

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
      <CallbackCard tone="pending" title={t('callback.notGuildMemberTitle')}>
        <p className="text-fg-muted text-sm">{t('callback.notGuildMemberDescription')}</p>
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
        <Button variant="ghost" asChild className="w-full">
          <Link to={paths.login}>{t('callback.backToLogin')}</Link>
        </Button>
      </CallbackCard>
    )
  }

  // status = Blocked on our side, with nothing to do with Discord or the guild - which is why it
  // offers no retry: retrying changes nothing (design/build.py, sc_callback). The only way out is
  // back to the login.
  if (error === 'user_blocked') {
    return (
      <CallbackCard tone="canceled" title={t('callback.blockedTitle')}>
        <p className="text-fg-muted text-sm">{t('callback.blockedDescription')}</p>
        <Button variant="outline" asChild className="w-full">
          <Link to={paths.login}>{t('callback.backToLogin')}</Link>
        </Button>
      </CallbackCard>
    )
  }

  if (error) {
    return (
      <CallbackCard tone="canceled" title={t('callback.genericErrorTitle')}>
        <p className="text-fg-muted text-sm">{t('callback.genericErrorDescription')}</p>
        <Button variant="outline" asChild className="w-full">
          <Link to={paths.login}>{t('callback.backToLogin')}</Link>
        </Button>
      </CallbackCard>
    )
  }

  if (isBootstrapping || (isAuthenticated && meQuery.isPending)) {
    return (
      <CallbackCard title={t('callback.loading')}>
        <p className="text-fg-muted text-sm">{t('callback.loadingDescription')}</p>
      </CallbackCard>
    )
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
