import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { LoadMore } from '@/components/LoadMore'
import { ErrorState } from '@/components/ErrorState'
import { StatusBadge, type StatusTone } from '@/components/StatusBadge'
import { Skeleton } from '@/components/ui/skeleton'
import { PageHeader } from '@/components/PageHeader'
import { notificationText, useMarkAllAsRead, useNotificationClick, useNotificationInbox } from '@/features/notifications'
import { relativeTimeFrom } from '@/lib/relativeTime'
import { cn } from '@/lib/utils'

/**
 * The badge's tone per type - only the ones that stand for a settled outcome (design/build.py
 * sc_notifications).
 *
 * **The tenth copy of the badge, and the one that hid the longest** (ADR-21 §6). It was not in
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
  // loaded (ADR-19 §4).
  const { data, isPending, isLoadingError, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useNotificationInbox()
  const markAllAsRead = useMarkAllAsRead()
  const handleClick = useNotificationClick()

  // "Ver más" accumulates the pages already fetched and shows them together (ADR-04 §6).
  const notifications = data?.pages.flatMap((page) => page.content) ?? []
  const total = data?.pages[0]?.totalElements ?? 0
  const unreadCount = notifications.filter((notification) => notification.readStatus === 'Unread').length

  function timeAgoLabel(createdAt: string) {
    const rel = relativeTimeFrom(createdAt)
    if (rel.unit === 'now') return t('time.now')
    return t('timeAgo', { value: t(`time.${rel.unit}`, { count: rel.count }) })
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title={t('title')}
        help="basics.notifications"
        action={unreadCount > 0 ? { label: t('markAllAsRead'), onClick: () => markAllAsRead.mutate() } : undefined}
      />
      {isPending && (
        <div className="space-y-2">
          {Array.from({ length: 4 }, (_, index) => (
            <Skeleton key={index} className="h-14 rounded-lg" />
          ))}
        </div>
      )}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && notifications.length === 0 && <EmptyState title={t('emptyTitle')} description={t('emptyDescription')} />}
      {notifications.length > 0 && (
        <>
          <ul className="list-divided">
            {notifications.map((notification) => {
              const unread = notification.readStatus === 'Unread'
              const outcome = OUTCOME_TONE[notification.notificationType]
              return (
                <li key={notification.id}>
                  <button
                    type="button"
                    onClick={() => handleClick(notification)}
                    className={cn(
                      'hover:bg-accent flex w-full cursor-pointer flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3 text-left',
                      unread ? 'bg-raised' : 'bg-transparent',
                    )}
                  >
                    <span className={cn('size-2 shrink-0 rounded-full', unread ? 'bg-brand-fg' : 'bg-transparent')} aria-hidden />
                    <span className={cn('min-w-0 flex-1 basis-40 text-sm wrap-anywhere', unread ? 'text-fg font-medium' : 'text-fg-muted')}>
                      {notificationText(notification, t).title}
                    </span>
                    {outcome && <StatusBadge tone={outcome.tone} label={t(outcome.labelKey)} />}
                    <span className="text-fg-subtle w-20 shrink-0 text-right text-xs">{timeAgoLabel(notification.createdAt)}</span>
                  </button>
                </li>
              )
            })}
          </ul>
          <LoadMore
            hasMore={hasNextPage}
            isLoading={isFetchingNextPage}
            onLoadMore={() => void fetchNextPage()}
            shown={notifications.length}
            total={total}
          />
        </>
      )}
    </div>
  )
}

export { NotificationsPage as Component }
