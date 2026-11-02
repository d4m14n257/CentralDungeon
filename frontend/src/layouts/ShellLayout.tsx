import { Outlet } from 'react-router'

import { AppHeader } from './components/AppHeader'

/**
 * The bare shell: header and content, with no section nav.
 *
 * It serves the screens that belong to no context - the inbox, support, the reader's own week and files,
 * the two profiles - and the 404 of an address outside every context, which is exactly why it has no
 * nav to show: their sections are not a context's sections. The header keeps
 * reporting whichever context the reader came from (ADR-20 §2).
 */
export function ShellLayout() {
  return (
    <div className="bg-background min-h-svh">
      <AppHeader />
      <main className="page-content">
        <Outlet />
      </main>
    </div>
  )
}
