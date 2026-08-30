import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { paths } from '@/config/paths'
import { NotificationBell } from '@/features/notifications'
import { useMe } from '@/features/users'
import type { AppContext } from '@/stores/contextStore'

import { ContextSwitcher } from './ContextSwitcher'
import { UserMenu } from './UserMenu'

export function AppHeader() {
  const { t } = useTranslation('common')
  const { data: me } = useMe()

  const availableContexts: AppContext[] = []
  if (me?.roles.includes('Player')) availableContexts.push('player')
  if (me?.roles.includes('Master')) availableContexts.push('master')

  return (
    <header className="border-border bg-background sticky top-0 z-10 border-b">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
        <Link to={paths.home} className="font-serif text-lg font-semibold">
          {t('nav.brand')}
        </Link>
        <div className="flex items-center gap-2">
          <ContextSwitcher availableContexts={availableContexts} />
          <NotificationBell />
          {me && <UserMenu displayName={me.name ?? '?'} />}
        </div>
      </div>
    </header>
  )
}
