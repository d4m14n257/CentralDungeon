import { Outlet } from 'react-router'

import { AppHeader } from './components/AppHeader'
import { MasterSectionNav } from './components/MasterSectionNav'

/**
 * The shell of the master context, /master/*.
 *
 * It checks no role, deliberately (#103), and that is not a gap: running a table is a row in
 * `masters`, not the `Master` role (#135), so a role check here would hide the screen from a
 * co-master who legitimately has it.
 */
export function MasterLayout() {
  return (
    <div className="bg-background min-h-svh">
      <AppHeader />
      <main className="mx-auto max-w-5xl space-y-6 px-4 py-6">
        <MasterSectionNav />
        <Outlet />
      </main>
    </div>
  )
}
