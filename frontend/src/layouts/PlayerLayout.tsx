import { Outlet } from 'react-router'

import { AppHeader } from './components/AppHeader'
import { PlayerSectionNav } from './components/PlayerSectionNav'

/**
 * The shell of the player context, `/player/*`: the explorer, a table's public detail, and the
 * reader's own applications and tables.
 *
 * Like every context layout, it does **not** check roles - the context is UI organisation, not
 * security (#103). Browsing tables belongs to the Player context rather than to "anyone with a
 * session" (#222), but that is a statement about where the screens live in the navigation, not a
 * gate: what needs the `Player` role is applying, and that is decided on the screen, with the reason
 * written on the disabled button.
 */
export function PlayerLayout() {
  return (
    <div className="bg-background min-h-svh">
      <AppHeader />
      <main className="mx-auto max-w-5xl space-y-6 px-4 py-6">
        <PlayerSectionNav />
        <Outlet />
      </main>
    </div>
  )
}
