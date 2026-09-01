import { Outlet } from 'react-router'

/**
 * Tarjeta centrada sobre el canvas, sin header: el retorno de OAuth y el onboarding.
 * /login queda fuera de este layout a propósito — se pinta a sangre con el gradiente de marca
 * (#132) y por eso arma su propio encuadre.
 */
export function PublicLayout() {
  return (
    <div className="flex min-h-svh items-center justify-center p-4">
      <Outlet />
    </div>
  )
}
