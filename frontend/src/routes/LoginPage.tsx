import { useTranslation } from 'react-i18next'

import { LanguageSwitch } from '@/components/LanguageSwitch'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { env } from '@/config/env'
import { useBackendStatus } from '@/hooks/useBackendStatus'
import { BrandMark } from '@/layouts/components/BrandMark'

/**
 * La puerta de entrada (frontend-diseno.md 4). Sobre el gradiente propio de la comunidad, el
 * único lugar donde aparece entero (#132). La letra chica no es decorativa: explica por qué no
 * hay registro ni contraseña, que es la primera pregunta de cualquiera que llega (#38).
 *
 * El cambio de idioma vive dentro de la tarjeta (#198). Es la única pantalla sin `UserMenu`, así que
 * sin esto alguien que cae en un idioma que no lee no tiene forma de saber que se puede cambiar —
 * y la detección del navegador, que resuelve el caso común, no lo resuelve para todos.
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
          // Sin backend, el <a> real llevaría a un error de conexión del navegador en vez de uno
          // explicado (principio 2, frontend-diseno.md 1) - un <a> no acepta `disabled`, así que
          // en este estado se reemplaza por un botón sin href en vez de deshabilitar el enlace.
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
