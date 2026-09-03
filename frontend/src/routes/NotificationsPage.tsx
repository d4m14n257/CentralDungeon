import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { notificationText, useMarkAllAsRead, useNotificationClick, useNotifications } from '@/features/notifications'
import { relativeTimeFrom } from '@/lib/relativeTime'
import { cn } from '@/lib/utils'

/** Tono del badge por tipo - solo los que representan un desenlace cerrado (design/build.py sc_notifications). */
const OUTCOME_TONE: Record<string, { badge: string; dot: string; labelKey: string }> = {
  RegistrationAccepted: { badge: 'bg-state-open-bg text-state-open-fg', dot: 'bg-state-open-dot', labelKey: 'badge.accepted' },
  RegistrationRejected: { badge: 'bg-state-canceled-bg text-state-canceled-fg', dot: 'bg-state-canceled-dot', labelKey: 'badge.rejected' },
}

/**
 * The full inbox, /notifications - what the bell only summarizes. Titles *and* messages here, and
 * "mark all as read".
 */
export function NotificationsPage() {
  const { t } = useTranslation('notifications')
  // isLoadingError, no isError: un refetch de fondo que falla no debe tapar una lista ya
  // cargada (docs/decisiones.md #150).
  const { data, isPending, isLoadingError, refetch } = useNotifications()
  const markAllAsRead = useMarkAllAsRead()
  const handleClick = useNotificationClick()

  const unreadCount = data?.content.filter((notification) => notification.readStatus === 'Unread').length ?? 0

  function timeAgoLabel(createdAt: string) {
    const rel = relativeTimeFrom(createdAt)
    if (rel.unit === 'now') return t('time.now')
    return t('timeAgo', { value: t(`time.${rel.unit}`, { count: rel.count }) })
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <h1 className="font-serif text-2xl font-semibold">{t('title')}</h1>
        {unreadCount > 0 && (
          <Button variant="ghost" size="sm" className="text-brand-fg" onClick={() => markAllAsRead.mutate()}>
            {t('markAllAsRead')}
          </Button>
        )}
      </div>
      {isPending && (
        <div className="space-y-2">
          {Array.from({ length: 4 }, (_, index) => (
            <Skeleton key={index} className="h-14 rounded-lg" />
          ))}
        </div>
      )}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && <EmptyState title={t('emptyTitle')} description={t('emptyDescription')} />}
      {data && data.content.length > 0 && (
        <ul className="divide-border divide-y rounded-lg border">
          {data.content.map((notification) => {
            const unread = notification.readStatus === 'Unread'
            const tone = OUTCOME_TONE[notification.notificationType]
            return (
              <li key={notification.id}>
                <button
                  type="button"
                  onClick={() => handleClick(notification)}
                  className={cn(
                    // hover:bg-accent no se nota sobre una fila "raised" - --color-accent es
                    // --color-raised en globals.css. Un tono propio, más fuerte, que sí cambia
                    // sobre las dos superficies (leída y no leída).
                    'hover:bg-border-strong/60! flex w-full cursor-pointer items-center gap-3 px-4 py-3 text-left',
                    unread ? 'bg-raised' : 'bg-transparent',
                  )}
                >
                  <span className={cn('size-2 shrink-0 rounded-full', unread ? 'bg-brand-fg' : 'bg-transparent')} aria-hidden />
                  <span className={cn('flex-1 truncate text-sm', unread ? 'text-fg font-medium' : 'text-fg-muted')}>
                    {notificationText(notification, t).title}
                  </span>
                  {tone && (
                    <span className={cn('inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium', tone.badge)}>
                      <span className={cn('size-1.5 rounded-full', tone.dot)} />
                      {t(tone.labelKey)}
                    </span>
                  )}
                  <span className="text-fg-subtle w-20 shrink-0 text-right text-xs">{timeAgoLabel(notification.createdAt)}</span>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

export { NotificationsPage as Component }
