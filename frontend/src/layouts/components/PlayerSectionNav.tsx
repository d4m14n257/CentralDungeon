import { useTranslation } from 'react-i18next'
import { NavLink } from 'react-router'

import { playerApplicationsPath, playerHistoryPath, playerHomePath, playerMyTablesPath } from '@/config/paths'
import { cn } from '@/lib/utils'

/**
 * The sections of the player context, as links. Same shape and same reason as `AdminSectionNav` and
 * `MasterSectionNav`.
 *
 * It arrives last of the three and that is the whole story: the player context went from one screen
 * to five without anyone going back to build it, so `/player/applications` and `/player/my-tables`
 * had no link anywhere in the application and could only be reached by typing the URL. The other two
 * contexts got their nav the moment they grew a second screen; this one did not.
 *
 * It grows with the context, in this list and not in a different one somewhere else: `/player/files`
 * joins here when F2 builds it. `/player/history` (#133) is the one already added, for the exact
 * reason this whole component exists - without an entry here it would be reachable only by typing
 * the URL, which is the bug F1's review found with this context the first time.
 */
export function PlayerSectionNav() {
  const { t } = useTranslation('tables')

  const sections = [
    { to: playerHomePath(), label: t('nav.explore'), end: true },
    { to: playerApplicationsPath(), label: t('nav.applications'), end: false },
    { to: playerMyTablesPath(), label: t('nav.myTables'), end: false },
    { to: playerHistoryPath(), label: t('nav.history'), end: false },
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
