import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { StatusBadge, type StatusTone } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { notificationText, useMarkAllAsRead, useNotificationClick, useNotifications } from '@/features/notifications'
import { relativeTimeFrom } from '@/lib/relativeTime'
import { cn } from '@/lib/utils'

/**
 * The badge's tone per type - only the ones that stand for a settled outcome (design/build.py
 * sc_notifications).
 *
 * **The tenth copy of the badge, and the one that hid the longest** (#261). It was not in
 * a feature's own `components/` folder like the other nine, so the first count of the duplication
 * missed it: a screen is a place a component gets written too. Now it is the same `StatusBadge` as
 * everywhere else, and what stays here is what belongs to notifications - which type counts as a
 * settled outcome.
 */
const OUTCOME_TONE: Record<string, { tone: StatusTone; labelKey: string }> = {
  RegistrationAccepted: { tone: 'open', labelKey: 'badge.accepted' },
  RegistrationRejected: { tone: 'canceled', labelKey: 'badge.rejected' },
}

/**
 * The full inbox, /notifications - what the bell only summarizes. Titles *and* messages here, and
 * "mark all as read".
 */
export function NotificationsPage() {
  const { t } = useTranslation('notifications')
  // isLoadingError, not isError: a background refetch that fails must not hide a list that already
  // loaded (docs/decisiones.md #150).
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
            const outcome = OUTCOME_TONE[notification.notificationType]
            return (
              <li key={notification.id}>
                <button
                  type="button"
                  onClick={() => handleClick(notification)}
                  className={cn(
                    // hover:bg-accent does not show on a "raised" row - --color-accent is
                    // --color-raised in globals.css. A stronger tone of its own, which does change
                    // over both surfaces (read and unread).
                    'hover:bg-border-strong/60! flex w-full cursor-pointer items-center gap-3 px-4 py-3 text-left',
                    unread ? 'bg-raised' : 'bg-transparent',
                  )}
                >
                  <span className={cn('size-2 shrink-0 rounded-full', unread ? 'bg-brand-fg' : 'bg-transparent')} aria-hidden />
                  <span className={cn('flex-1 truncate text-sm', unread ? 'text-fg font-medium' : 'text-fg-muted')}>
                    {notificationText(notification, t).title}
                  </span>
                  {outcome && <StatusBadge tone={outcome.tone} label={t(outcome.labelKey)} />}
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
