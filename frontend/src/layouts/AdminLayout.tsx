import { Outlet } from 'react-router'

import { AdminSectionNav } from './components/AdminSectionNav'
import { AppHeader } from './components/AppHeader'

/**
 * The shell of the admin context, /admin/*.
 *
 * No role check here either (#103): someone who forces the route without the role gets a 403 from
 * the backend and the screen paints `ForbiddenState`.
 */
export function AdminLayout() {
  return (
    <div className="bg-background min-h-svh">
      <AppHeader />
      <main className="mx-auto max-w-5xl space-y-6 px-4 py-6">
        <AdminSectionNav />
        <Outlet />
      </main>
    </div>
  )
}
