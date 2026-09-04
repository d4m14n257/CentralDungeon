import { useTranslation } from 'react-i18next'
import { NavLink } from 'react-router'

import { masterDashboardPath, masterTablesPath } from '@/config/paths'
import { cn } from '@/lib/utils'

/**
 * The sections of the master context, as links. Same shape and same reason as `AdminSectionNav`.
 *
 * The context switcher gets somebody *into* /master, and until the tray existed there was one
 * screen there, so landing on it was enough. With two, there has to be a way across or one of them
 * is a route only a typed URL can reach.
 *
 * It grows with the context, in this list and not in a different one somewhere else.
 */
export function MasterSectionNav() {
  const { t } = useTranslation('master')

  const sections = [
    { to: masterDashboardPath(), label: t('nav.dashboard'), end: true },
    { to: masterTablesPath(), label: t('nav.tables'), end: false },
  ]

  return (
    <nav aria-label={t('nav.label')} className="border-border flex gap-1 border-b">
      {sections.map((section) => (
        <NavLink
          key={section.to}
          to={section.to}
          end={section.end}
          className={({ isActive }) =>
            cn(
              'border-b-2 px-3 py-2 text-sm transition-colors',
              isActive ? 'border-brand-400 text-fg font-medium' : 'text-fg-muted hover:text-fg border-transparent',
            )
          }
        >
          {section.label}
        </NavLink>
      ))}
    </nav>
  )
}
