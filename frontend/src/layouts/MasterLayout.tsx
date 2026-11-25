import { Outlet } from 'react-router'

import { AppHeader } from './components/AppHeader'
import { RequireContext } from './components/RequireContext'
import { MasterSectionNav } from './components/MasterSectionNav'

/**
 * The shell of the master context, /master/*.
 *
 * Only for an account that has the context: the `Master` role **or** a live row in `masters`, because running a table is membership, not the role - a co-master assigned without it
 * still runs theirs. Anybody else is sent back to their own home before this shell paints.
 */
export function MasterLayout() {
  return (
    <RequireContext context="master">
      <div className="bg-background min-h-svh">
        <AppHeader />
        <main className="page-content space-y-6">
          <MasterSectionNav />
          <Outlet />
        </main>
      </div>
    </RequireContext>
  )
}
