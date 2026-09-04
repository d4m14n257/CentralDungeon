import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router'

import { helpPath, type HelpAudience } from '@/config/paths'
import { useMe } from '@/features/users'
import { cn } from '@/lib/utils'

import { canReadHelp } from './HelpAudienceGate'

const AUDIENCES: HelpAudience[] = ['players', 'masters', 'admins']

/**
 * The system's help (decisiones.md #167), split by audience (#168): what serves everybody lives in
 * the index and each role has its own page, so nobody has to read somebody else's help to find
 * theirs. The sections are child routes rather than tabs with `useState`, so each has its own URL
 * and is linked by its `#ref` from the screen that needs it (arquitectura.md 3.1.6).
 *
 * The navigation **only offers the reader's own audiences** (#169): a player does not see the admin
 * tab. Reaching it by URL anyway is cut off in `HelpAudienceGate`.
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
