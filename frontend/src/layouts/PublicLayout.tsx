import { Outlet } from 'react-router'

/**
 * A card centred on the canvas, with no header: the OAuth return and the onboarding.
 * /login is deliberately outside this layout — it is painted full-bleed over the brand gradient
 * (#132) and therefore builds its own frame.
 */
export function PublicLayout() {
  return (
    <div className="flex min-h-svh items-center justify-center p-4">
      <Outlet />
    </div>
  )
}
