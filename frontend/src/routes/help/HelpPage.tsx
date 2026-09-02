import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router'

import { helpPath, type HelpAudience } from '@/config/paths'
import { useMe } from '@/features/users'
import { cn } from '@/lib/utils'

import { canReadHelp } from './HelpAudienceGate'

const AUDIENCES: HelpAudience[] = ['players', 'masters', 'admins']

/**
 * La ayuda del sistema (decisiones.md #167), partida por audiencia (#168): lo que sirve a todos
 * vive en el índice y cada rol tiene su página, para que nadie tenga que leer la ayuda de otro
 * para encontrar la suya. Las secciones son rutas hijas, no pestañas con `useState`, así cada una
 * tiene URL propia y se enlaza con su `#ref` desde la pantalla que la necesita (arquitectura.md 3.1.6).
 *
 * La navegación **solo ofrece las audiencias del usuario** (#169): un jugador no ve la pestaña de
 * admins. Entrar por URL igual queda cortado, en `HelpAudienceGate`.
 */
export function HelpPage() {
  const { t } = useTranslation('help')
  const { data: me } = useMe()

  const visibleAudiences = me ? AUDIENCES.filter((audience) => canReadHelp(audience, me)) : []

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="font-serif text-2xl font-semibold">{t('title')}</h1>
        <p className="text-fg-muted text-sm">{t('intro')}</p>
      </div>

      <nav aria-label={t('title')} className="border-border flex flex-wrap gap-1 border-b">
        <HelpTab to={helpPath()} label={t('nav.basics')} end />
        {visibleAudiences.map((audience) => (
          <HelpTab key={audience} to={helpPath(audience)} label={t(`nav.${audience}`)} />
        ))}
      </nav>

      <Outlet />
    </div>
  )
}

function HelpTab({ to, label, end }: { to: string; label: string; end?: boolean }) {
  return (
    <NavLink
      to={to}
      end={end ?? false}
      className={({ isActive }) =>
        cn(
          'border-b-2 px-3 py-2 text-sm',
          isActive ? 'border-primary text-fg font-medium' : 'text-fg-muted hover:text-fg border-transparent',
        )
      }
    >
      {label}
    </NavLink>
  )
}

export { HelpPage as Component }
