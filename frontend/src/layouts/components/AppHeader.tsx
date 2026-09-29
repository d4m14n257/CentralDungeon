import { useEffect } from 'react'
import { Link } from 'react-router'

import { homePathFor } from '@/config/paths'
import { NotificationBell } from '@/features/notifications'
import { useMe } from '@/features/users'
import { useAvailableContexts } from '@/hooks/useAvailableContexts'
import { useContextStore } from '@/stores/contextStore'

import { BrandMark } from './BrandMark'
import { ContextSwitcher } from './ContextSwitcher'
import { UserMenu } from './UserMenu'

/**
 * The shell's bar (frontend-diseno.md 2): wordmark, context, notifications and avatar.
 * It sits on `surface` rather than on the canvas — that is what separates it from the content
 * without a shadow.
 *
 * **It never pushes the page sideways** (skill `diseno` §5.b). Below `sm` the gaps and the wordmark
 * get smaller, and if even that does not fit - a 320 px phone with a long context name - the wordmark
 * is what gives way (`min-w-0` + `truncate`): the switcher, the bell and the avatar are the controls,
 * and none of them can be the one that ends up off screen.
 */
export function AppHeader() {
  const { data: me } = useMe()
  const { contexts, activeContext } = useAvailableContexts()
  const setActiveContext = useContextStore((state) => state.setActiveContext)

  // The URL is what says which context is on screen (#222), so the remembered value follows it
  // rather than the other way round. That is what keeps `/notifications` and `/help` - which belong
  // to no context - showing the one the reader came from instead of resetting it.
  //
  // Only a context the account actually has gets remembered. A master with no Player role who opens
  // `/player` reads "Jugador" while they are there, which is true, but remembering it would leave
  // that chip on every transversal screen afterwards - saying they are in a context that is not on
  // their menu and that they cannot go back to.
  const worthRemembering = contexts.includes(activeContext)
  useEffect(() => {
    if (worthRemembering) {
      setActiveContext(activeContext)
    }
  }, [activeContext, worthRemembering, setActiveContext])

  return (
    <header className="border-border bg-surface sticky top-0 z-10 border-b">
      <div className="mx-auto flex max-w-5xl items-center gap-2 px-4 py-3 sm:gap-4">
        {/* The logo goes back to the home of the context on screen, the same place the switcher lands. */}
        <Link to={homePathFor(activeContext)} className="min-w-0 truncate">
          <BrandMark className="text-sm sm:text-base" />
        </Link>
        <ContextSwitcher availableContexts={contexts} activeContext={activeContext} />
        <span className="flex-1" />
        <NotificationBell />
        {me && <UserMenu displayName={me.name} />}
      </div>
    </header>
  )
}
