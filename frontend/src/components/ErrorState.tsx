import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'

interface ErrorStateProps {
  message?: string
  onRetry?: () => void
}

export function ErrorState({ message, onRetry }: ErrorStateProps) {
  const { t } = useTranslation('common')

  return (
    <div className="border-border flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed py-16 text-center">
      <p className="font-medium">{t('states.errorTitle')}</p>
      <p className="text-muted-foreground max-w-sm text-sm">{message ?? t('states.errorDescription')}</p>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          {t('actions.retry')}
        </Button>
      )}
    </div>
  )
}
