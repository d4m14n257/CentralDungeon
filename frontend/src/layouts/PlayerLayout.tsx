import { Outlet } from 'react-router'

import { AppHeader } from './components/AppHeader'
import { RequireContext } from './components/RequireContext'
import { PlayerSectionNav } from './components/PlayerSectionNav'

/**
 * The shell of the player context, `/player/*`: the explorer, a table's public detail, and the
 * reader's own applications and tables.
 *
 * Only for an account with the `Player` role (ADR-20 §2): anybody else is sent back to their own home
 * before this shell paints. It is a statement about what the interface shows, not the security -
 * the backend still authorizes every endpoint on its own (ADR-20 §1).
 */
export function PlayerLayout() {
  return (
    <RequireContext context="player">
      <div className="bg-background min-h-svh">
        <AppHeader />
        <main className="page-content space-y-6">
          <PlayerSectionNav />
          <Outlet />
        </main>
      </div>
    </RequireContext>
  )
}
