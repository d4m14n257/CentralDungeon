import { Bell } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { relativeTimeFrom } from '@/lib/relativeTime'
import { cn } from '@/lib/utils'

import { useNotifications } from '../api/useNotifications'
import { useNotificationClick } from '../hooks/useNotificationClick'

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
            /* El contador es acento sólido, no destructivo: notificar no es un error. */
            <Badge className="bg-primary text-primary-foreground absolute -top-1 -right-1 h-4 min-w-4 justify-center rounded-full px-1 text-xs font-bold">
              {unreadCount}
            </Badge>
          )}
        </Button>
      </DropdownMenuTrigger>
      {/* Máximo 5 (notificationsApi.list pide size=5), diseño de design/build.py sc_comp_shell:
          punto lleno + fila resaltada para lo no leído, punto hueco y texto atenuado para lo leído. */}
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
                // --color-accent es --color-raised (globals.css): el focus:bg-accent de base no se
                // nota sobre una fila ya "raised" - se pisa con un tono propio y más fuerte que
                // funciona igual en las leídas y las no leídas, más cursor-pointer (la base trae
                // cursor-default, pensado para ítems no clickeables).
                'focus:bg-border-strong! gap-2.5 px-3 py-2 text-[13px] cursor-pointer',
                unread ? 'bg-raised text-fg' : 'text-fg-muted',
              )}
            >
              <span className={cn('w-[15px] shrink-0 text-center', unread ? 'text-brand-fg' : 'text-fg-subtle')} aria-hidden>
                {unread ? '●' : '○'}
              </span>
              <span className="flex-1 truncate">{notification.title}</span>
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
