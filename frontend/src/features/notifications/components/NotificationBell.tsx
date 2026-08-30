import { Bell } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

import { useNotifications } from '../api/useNotifications'

export function NotificationBell() {
  const { t } = useTranslation('notifications')
  const { data } = useNotifications()

  const unreadCount = data?.content.filter((notification) => notification.readStatus === 'Unread').length ?? 0
  const recent = data?.content.slice(0, 5) ?? []

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="relative" aria-label={t('title')}>
          <Bell className="size-5" />
          {unreadCount > 0 && (
            <Badge variant="destructive" className="absolute -top-1 -right-1 h-4 min-w-4 justify-center rounded-full px-1 text-[10px]">
              {unreadCount}
            </Badge>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80">
        <DropdownMenuLabel>{t('title')}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {recent.length === 0 && <p className="text-muted-foreground px-2 py-4 text-center text-sm">{t('emptyTitle')}</p>}
        {recent.map((notification) => (
          <DropdownMenuItem key={notification.id} className="flex flex-col items-start gap-0.5">
            <span className="text-sm font-medium">{notification.title}</span>
            {notification.message && <span className="text-muted-foreground text-xs">{notification.message}</span>}
          </DropdownMenuItem>
        ))}
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link to="/notifications">{t('title')}</Link>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
