import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router'

import { helpPath, type HelpAudience } from '@/config/paths'
import { cn } from '@/lib/utils'

const AUDIENCES: { key: 'basics' | HelpAudience; to: string }[] = [
  { key: 'basics', to: helpPath() },
  { key: 'players', to: helpPath('players') },
  { key: 'masters', to: helpPath('masters') },
  { key: 'admins', to: helpPath('admins') },
]

/**
 * La ayuda del sistema (decisiones.md #167), partida por audiencia (#168): lo que sirve a todos
 * vive en el índice y cada rol tiene su página, para que nadie tenga que leer la ayuda de otro
 * para encontrar la suya. Las secciones son rutas hijas, no `useState`, así cada una tiene URL
 * propia y se puede enlazar con su `#ref` desde la pantalla que la necesita (arquitectura.md 3.1.6).
 */
export function HelpPage() {
  const { t } = useTranslation('help')

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="font-serif text-2xl font-semibold">{t('title')}</h1>
        <p className="text-fg-muted text-sm">{t('intro')}</p>
      </div>

      <nav aria-label={t('title')} className="border-border flex flex-wrap gap-1 border-b">
        {AUDIENCES.map(({ key, to }) => (
          <NavLink
            key={key}
            to={to}
            end={key === 'basics'}
            className={({ isActive }) =>
              cn(
                'border-b-2 px-3 py-2 text-sm',
                isActive ? 'border-primary text-fg font-medium' : 'text-fg-muted hover:text-fg border-transparent',
              )
            }
          >
            {t(`nav.${key}`)}
          </NavLink>
        ))}
      </nav>

      <Outlet />
    </div>
  )
}

export { HelpPage as Component }
