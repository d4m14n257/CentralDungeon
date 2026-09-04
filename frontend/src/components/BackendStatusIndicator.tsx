import { useTranslation } from 'react-i18next'

import { useBackendStatus } from '@/hooks/useBackendStatus'
import { cn } from '@/lib/utils'

/**
 * The global backend-connectivity indicator. It lives in RootLayout so it shows on every screen,
 * including /login, which has no header. It reuses the state tokens already measured for AA
 * (open=verde, canceled=rojo, frontend-diseno.md 3) en vez de inventar un color nuevo.
 */
export function BackendStatusIndicator() {
  const { t } = useTranslation('common')
  const { isOnline } = useBackendStatus()

  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        'fixed bottom-4 left-4 z-40 inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium shadow-sm',
        isOnline ? 'bg-state-open-bg text-state-open-fg' : 'bg-state-canceled-bg text-state-canceled-fg',
      )}
    >
      <span className={cn('size-1.5 rounded-full', isOnline ? 'bg-state-open-dot' : 'bg-state-canceled-dot')} />
      {isOnline ? t('connection.online') : t('connection.offline')}
    </div>
  )
}
