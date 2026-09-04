import { useTranslation } from 'react-i18next'

import { LanguageSwitch } from '@/components/LanguageSwitch'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { env } from '@/config/env'
import { useBackendStatus } from '@/hooks/useBackendStatus'
import { BrandMark } from '@/layouts/components/BrandMark'

/**
 * The front door (frontend-diseno.md 4). Over the community's own gradient, the one place it shows
 * in full (#132). The small print is not decoration: it explains why there is no sign-up and no
 * password, which is the first question anybody arriving asks (#38).
 *
 * The language switch lives inside the card (#198). This is the only screen with no `UserMenu`, so
 * without it somebody landing in a language they do not read has no way to know it can be changed —
 * and browser detection, which handles the common case, does not handle everybody's.
 */
export function LoginPage() {
  const { t } = useTranslation('auth')
  const { isOnline } = useBackendStatus()

  return (
    <div className="bg-brand-gradient flex min-h-svh items-center justify-center p-4">
      <Card className="border-border-strong w-full max-w-sm gap-0 p-8 text-center">
        <BrandMark className="font-serif text-2xl font-bold" />
        <p className="text-fg-muted mt-2 text-sm">{t('login.subtitle')}</p>
        {isOnline ? (
          <Button asChild size="lg" className="mt-6 w-full">
            <a href={`${env.apiBaseUrl}/oauth2/authorization/discord`}>{t('login.cta')}</a>
          </Button>
        ) : (
          // With no backend, a real <a> would lead to the browser's own connection error instead of
          // an explained one (principio 2, frontend-diseno.md 1) - an <a> takes no `disabled`, so in
          // this state it is replaced by a button with no href rather than a disabled link.
          <Button size="lg" className="mt-6 w-full" disabled>
            {t('login.cta')}
          </Button>
        )}
        <p className="text-fg-subtle mt-4 text-xs">{isOnline ? t('login.disclaimer') : t('login.serverUnavailable')}</p>
        <LanguageSwitch className="border-border mt-6 border-t pt-4" />
      </Card>
    </div>
  )
}

export { LoginPage as Component }
