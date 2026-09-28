import { Outlet } from 'react-router'

import { AppHeader } from './components/AppHeader'
import { RequireContext } from './components/RequireContext'
import { MasterSectionNav } from './components/MasterSectionNav'

/**
 * The shell of the master context, /master/*.
 *
 * Only for an account that has the context (#269): the `Master` role **or** a live row in `masters`
 * (#135), because running a table is membership, not the role - a co-master assigned without it
 * still runs theirs. Anybody else is sent back to their own home before this shell paints.
 */
export function MasterLayout() {
  return (
    <RequireContext context="master">
      <div className="bg-background min-h-svh">
        <AppHeader />
        <main className="mx-auto max-w-5xl space-y-6 px-4 py-6">
          <MasterSectionNav />
          <Outlet />
        </main>
      </div>
    </RequireContext>
  )
}
