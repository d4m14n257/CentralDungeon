import { Outlet } from 'react-router'

export function PublicLayout() {
  return (
    <div className="bg-background flex min-h-svh items-center justify-center p-4">
      <Outlet />
    </div>
  )
}
