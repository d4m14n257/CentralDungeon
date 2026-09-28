import { useTranslation } from 'react-i18next'
import { NavLink } from 'react-router'

import {
  adminCatalogsPath,
  adminFilesPath,
  adminHomePath,
  adminQueuePath,
  adminRequestsPath,
  adminSettingsPath,
  adminTablesPath,
  adminUsersPath,
} from '@/config/paths'
import { cn } from '@/lib/utils'

/**
 * The sections of the admin context, as links.
 *
 * The context switcher gets somebody *into* /admin, and until now the only thing there was one
 * screen, so landing on it was enough. With a second one there has to be a way across, or
 * /admin/catalogs is a route only a typed URL can reach.
 *
 * It grows with the context: /admin/users joined it with F3.1, /admin/requests with F3.2,
 * /admin/queue with F3.3, /admin/settings with F3.5, and the rest of the sitemap joins this list as
 * its screens land, not a different one somewhere else.
 *
 * **The home goes first, and it is where the context starts** (`homePathFor`, #270). It matches only
 * `/admin` itself (`end`), or it would stay underlined on every admin screen. **The tray comes right
 * after it**: of the working screens it is the one that says what to do next, so a listing sitting
 * there would say the context is about browsing. It is about resolving.
 */
export function AdminSectionNav() {
  const { t } = useTranslation('admin')

  const sections = [
    { to: adminHomePath(), label: t('nav.home'), end: true },
    { to: adminQueuePath(), label: t('nav.queue') },
    { to: adminTablesPath(), label: t('nav.tables') },
    { to: adminCatalogsPath(), label: t('nav.catalogs') },
    { to: adminFilesPath(), label: t('nav.files') },
    { to: adminUsersPath(), label: t('nav.users') },
    { to: adminRequestsPath(), label: t('nav.requests') },
    // Last on purpose: configuration is the section somebody visits on purpose, never the one they
    // work from, so it sits at the end of a nav read left to right.
    { to: adminSettingsPath(), label: t('nav.settings') },
  ]

  return (
    <nav aria-label={t('nav.label')} className="border-border flex gap-1 border-b">
      {sections.map((section) => (
        <NavLink
          key={section.to}
          to={section.to}
          end={section.end ?? false}
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
