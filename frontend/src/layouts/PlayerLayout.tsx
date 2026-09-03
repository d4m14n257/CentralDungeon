import { Outlet } from 'react-router'

import { AppHeader } from './components/AppHeader'

/**
 * The shell of the player context: the explorer, a table's public detail, and /my/*.
 *
 * Like every context layout, it does **not** check roles - the context is UI organisation, not
 * security (#103).
 */
export function PlayerLayout() {
  return (
    <div className="bg-background min-h-svh">
      <AppHeader />
      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
