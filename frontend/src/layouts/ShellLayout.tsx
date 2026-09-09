import { Outlet } from 'react-router'

import { AppHeader } from './components/AppHeader'

/**
 * The bare shell: header and content, with no section nav.
 *
 * It serves the two screens that belong to no context - `/notifications` and `/help` - which is
 * exactly why it has no nav to show: their sections are not a context's sections. The header keeps
 * reporting whichever context the reader came from (#222).
 */
export function ShellLayout() {
  return (
    <div className="bg-background min-h-svh">
      <AppHeader />
      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
