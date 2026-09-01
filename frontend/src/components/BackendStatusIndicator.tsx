import { useTranslation } from 'react-i18next'

import { useBackendStatus } from '@/hooks/useBackendStatus'
import { cn } from '@/lib/utils'

/**
 * Indicador global de conectividad con el backend. Vive en RootLayout para verse en cualquier
 * pantalla, incluida /login, que no tiene header. Reusa los tokens de estado ya medidos para AA
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
