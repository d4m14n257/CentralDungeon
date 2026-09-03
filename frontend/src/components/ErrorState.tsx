import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'

interface ErrorStateProps {
  message?: string
  onRetry?: () => void
}

/**
 * The error state of a screen: the request failed and there is nothing to render.
 *
 * It always offers a way forward - retrying, or going somewhere that works. A dead end with an error
 * message is where a session ends.
 *
 * @param props.title   what failed, in the user's words
 * @param props.description optional detail
 * @param props.onRetry optional retry, when the failure is worth trying again
 */
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
