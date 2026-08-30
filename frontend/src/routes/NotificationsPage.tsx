import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useMarkAsRead, useNotifications } from '@/features/notifications'
import { cn } from '@/lib/utils'

export function NotificationsPage() {
  const { t } = useTranslation('notifications')
  const { data, isPending, isError, refetch } = useNotifications()
  const markAsRead = useMarkAsRead()

  return (
    <div className="space-y-4">
      <h1 className="font-serif text-2xl font-semibold">{t('title')}</h1>
      {isPending && (
        <div className="space-y-2">
          {Array.from({ length: 4 }, (_, index) => (
            <Skeleton key={index} className="h-16 rounded-lg" />
          ))}
        </div>
      )}
      {isError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && <EmptyState title={t('emptyTitle')} description={t('emptyDescription')} />}
      {data && data.content.length > 0 && (
        <ul className="divide-border divide-y rounded-lg border">
          {data.content.map((notification) => (
            <li
              key={notification.id}
              className={cn('flex items-start justify-between gap-4 px-4 py-3', notification.readStatus === 'Unread' && 'bg-accent/40')}
            >
              <div className="space-y-1">
                <p className="text-sm font-medium">{notification.title}</p>
                {notification.message && <p className="text-muted-foreground text-sm">{notification.message}</p>}
              </div>
              {notification.readStatus === 'Unread' && (
                <Button variant="ghost" size="sm" onClick={() => markAsRead.mutate(notification.id)}>
                  {t('markAsRead')}
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export { NotificationsPage as Component }
