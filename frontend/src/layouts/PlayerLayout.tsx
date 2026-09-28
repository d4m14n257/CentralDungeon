import { Outlet } from 'react-router'

import { AppHeader } from './components/AppHeader'
import { RequireContext } from './components/RequireContext'
import { PlayerSectionNav } from './components/PlayerSectionNav'

/**
 * The shell of the player context, `/player/*`: the explorer, a table's public detail, and the
 * reader's own applications and tables.
 *
 * Only for an account with the `Player` role (#269): anybody else is sent back to their own home
 * before this shell paints. It is a statement about what the interface shows, not the security -
 * the backend still authorizes every endpoint on its own (#103).
 */
export function PlayerLayout() {
  return (
    <RequireContext context="player">
      <div className="bg-background min-h-svh">
        <AppHeader />
        <main className="mx-auto max-w-5xl space-y-6 px-4 py-6">
          <PlayerSectionNav />
          <Outlet />
        </main>
      </div>
    </RequireContext>
  )
}
