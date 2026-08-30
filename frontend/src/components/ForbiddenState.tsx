import { useTranslation } from 'react-i18next'

export function ForbiddenState() {
  const { t } = useTranslation('common')

  return (
    <div className="border-border flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed py-16 text-center">
      <p className="font-medium">{t('states.forbiddenTitle')}</p>
      <p className="text-muted-foreground max-w-sm text-sm">{t('states.forbiddenDescription')}</p>
    </div>
  )
}
