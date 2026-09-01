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
 * Los cinco estados del retorno de OAuth (design/out/screen-auth-callback.html). El de
 * verificación queda como título llano; los otros cuatro llevan el título adentro de un chip de
 * tono, que reemplaza al heading - así lee cada uno como lo que es (un paso que espera, uno que
 * se cortó) en vez de un heading gris repetido cuatro veces. El wordmark arriba es lo único que
 * esta pantalla comparte con /login - sin header, es la única marca de que seguís en CentralDungeon.
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

  // status = Blocked del lado nuestro, sin relación con Discord ni con el guild - por eso no
  // ofrece reintentar, reintentar no cambia nada (design/build.py, sc_callback). La única salida
  // es volver al login.
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
