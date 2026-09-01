import { Outlet } from 'react-router'

import { AppHeader } from './components/AppHeader'

export function AdminLayout() {
  return (
    <div className="bg-background min-h-svh">
      <AppHeader />
      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
