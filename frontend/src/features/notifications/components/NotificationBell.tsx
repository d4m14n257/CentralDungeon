import { Bell } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { relativeTimeFrom } from '@/lib/relativeTime'
import { cn } from '@/lib/utils'

import { notificationText } from '../lib/notificationText'

import { useNotifications } from '../api/useNotifications'
import { useNotificationClick } from '../hooks/useNotificationClick'

/**
 * The header's bell: the unread count, and the last few notifications in a dropdown.
 *
 * It shows **titles only**, which is why every notification's title has to be readable on its own
 * (#156). Clicking one marks it read and navigates to what it is about.
 */
export function NotificationBell() {
  const { t } = useTranslation('notifications')
  const { data } = useNotifications()
  const handleClick = useNotificationClick()

  const unreadCount = data?.content.filter((notification) => notification.readStatus === 'Unread').length ?? 0
  const recent = data?.content ?? []

  function relativeTimeLabel(createdAt: string) {
    const rel = relativeTimeFrom(createdAt)
    return rel.unit === 'now' ? t('time.now') : t(`time.${rel.unit}`, { count: rel.count })
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="relative" aria-label={t('title')}>
          <Bell className="size-5" />
          {unreadCount > 0 && (
            /* The counter is a solid accent and not a destructive one: being notified is not an error. */
            <Badge className="bg-primary text-primary-foreground absolute -top-1 -right-1 h-4 min-w-4 justify-center rounded-full px-1 text-xs font-bold">
              {unreadCount}
            </Badge>
          )}
        </Button>
      </DropdownMenuTrigger>
      {/* At most 5 (notificationsApi.list asks for size=5), design from design/build.py sc_comp_shell:
          a filled dot and a raised row for the unread, a hollow dot and muted text for the read. */}
      <DropdownMenuContent align="end" className="w-72 p-0 py-1">
        <div className="text-fg-subtle border-border border-b px-3 py-2 text-[11px] tracking-wide uppercase">
          {t('title')}
          {unreadCount > 0 && <> &middot; {t('unreadCount', { count: unreadCount })}</>}
        </div>
        {recent.length === 0 && <p className="text-fg-muted px-3 py-4 text-center text-sm">{t('emptyTitle')}</p>}
        {recent.map((notification) => {
          const unread = notification.readStatus === 'Unread'
          return (
            <DropdownMenuItem
              key={notification.id}
              onSelect={() => handleClick(notification)}
              className={cn(
                // --color-accent is --color-raised (globals.css), so the base focus:bg-accent does
                // not show on a row that is already raised - it is overridden with a stronger tone of
                // its own that works on read and unread rows alike, plus cursor-pointer (the base
                // ships cursor-default, meant for items that cannot be clicked).
                'focus:bg-border-strong! gap-2.5 px-3 py-2 text-[13px] cursor-pointer',
                unread ? 'bg-raised text-fg' : 'text-fg-muted',
              )}
            >
              <span className={cn('w-[15px] shrink-0 text-center', unread ? 'text-brand-fg' : 'text-fg-subtle')} aria-hidden>
                {unread ? '●' : '○'}
              </span>
              <span className="flex-1 truncate">{notificationText(notification, t).title}</span>
              <span className="text-fg-subtle shrink-0 text-[11px]">{relativeTimeLabel(notification.createdAt)}</span>
            </DropdownMenuItem>
          )
        })}
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild className="focus:bg-border-strong! cursor-pointer justify-center px-3 py-2 text-[13px]">
          <Link to="/notifications" className="text-brand-fg">
            {t('viewAll')}
          </Link>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
