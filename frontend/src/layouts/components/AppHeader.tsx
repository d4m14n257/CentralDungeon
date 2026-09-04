import { Link } from 'react-router'

import { adminTablesPath, masterTablesPath } from '@/config/paths'
import { NotificationBell } from '@/features/notifications'
import { useMe } from '@/features/users'
import { useContextStore, type AppContext } from '@/stores/contextStore'

import { BrandMark } from './BrandMark'
import { ContextSwitcher } from './ContextSwitcher'
import { UserMenu } from './UserMenu'

/**
 * The shell's bar (frontend-diseno.md 2): wordmark, context, notifications and avatar.
 * It sits on `surface` rather than on the canvas — that is what separates it from the content
 * without a shadow.
 */
export function AppHeader() {
  const { data: me } = useMe()
  const { activeContext } = useContextStore()

  // The Master context shows with the platform role or with at least one live row in `masters`
  // (decisiones.md #135) - somebody running a single table, assigned without the role, still runs it.
  const availableContexts: AppContext[] = []
  if (me?.roles.includes('Player')) availableContexts.push('player')
  if (me?.roles.includes('Master') || me?.hasManagedTables) availableContexts.push('master')
  if (me?.roles.includes('Admin') || me?.roles.includes('Owner')) availableContexts.push('admin')

  // The logo goes back to the active context's "home", the same way ContextSwitcher does on a
  // context change - /master does not exist yet (the Master dashboard, F1 #136), so the destination
  // in that context is /master/tables, E1's real entry point (decisiones.md #151). It is recomputed
  // against the current account's availableContexts rather than trusting the persisted value raw:
  // activeContext is a store with `persist` in localStorage, which knows nothing about which account
  // is signed in - switching accounts in the same browser can leave the previous session's context
  // behind (decisiones.md #156; no real exploit, it only misdirects the logo's link, and no /master
  // endpoint trusts this value).
  const effectiveContext: AppContext = availableContexts.includes(activeContext) ? activeContext : 'player'
  const homePath = effectiveContext === 'master' ? masterTablesPath() : effectiveContext === 'admin' ? adminTablesPath() : '/'

  return (
    <header className="border-border bg-surface sticky top-0 z-10 border-b">
      <div className="mx-auto flex max-w-5xl items-center gap-4 px-4 py-3">
        <Link to={homePath}>
          <BrandMark />
        </Link>
        <ContextSwitcher availableContexts={availableContexts} />
        <span className="flex-1" />
        <NotificationBell />
        {me && <UserMenu displayName={me.name} />}
      </div>
    </header>
  )
}
