import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { env } from '@/config/env'

export function LoginPage() {
  const { t } = useTranslation('auth')

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <CardTitle>{t('login.title')}</CardTitle>
        <CardDescription>{t('login.subtitle')}</CardDescription>
      </CardHeader>
      <CardContent>
        <Button asChild className="w-full">
          <a href={`${env.apiBaseUrl}/oauth2/authorization/discord`}>{t('login.cta')}</a>
        </Button>
      </CardContent>
    </Card>
  )
}

export { LoginPage as Component }
