import { Outlet } from 'react-router'

import { AdminSectionNav } from './components/AdminSectionNav'
import { AppHeader } from './components/AppHeader'
import { RequireContext } from './components/RequireContext'

/**
 * The shell of the admin context, /admin/*.
 *
 * Only for `Admin` or `Owner` (ADR-20 §2): anybody else is sent back to their own home before this shell
 * paints - not even the section nav reaches them. The backend still answers `403` on every admin
 * endpoint on its own (ADR-20 §1); this decides what the interface shows.
 */
export function AdminLayout() {
  return (
    <RequireContext context="admin">
      <div className="bg-background min-h-svh">
        <AppHeader />
        <main className="page-content space-y-6">
          <AdminSectionNav />
          <Outlet />
        </main>
      </div>
    </RequireContext>
  )
}
