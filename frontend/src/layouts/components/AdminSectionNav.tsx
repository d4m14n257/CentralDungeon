import { useTranslation } from 'react-i18next'
import { NavLink } from 'react-router'

import { adminCatalogsPath, adminFilesPath, adminRequestsPath, adminTablesPath, adminUsersPath } from '@/config/paths'
import { cn } from '@/lib/utils'

/**
 * The sections of the admin context, as links.
 *
 * The context switcher gets somebody *into* /admin, and until now the only thing there was one
 * screen, so landing on it was enough. With a second one there has to be a way across, or
 * /admin/catalogs is a route only a typed URL can reach.
 *
 * It grows with the context: /admin/users joined it with F3.1, /admin/requests with F3.2, and
 * /admin/queue and the rest of the sitemap join this list as their screens land, not a different one
 * somewhere else.
 */
export function AdminSectionNav() {
  const { t } = useTranslation('admin')

  const sections = [
    { to: adminTablesPath(), label: t('nav.tables') },
    { to: adminCatalogsPath(), label: t('nav.catalogs') },
    { to: adminFilesPath(), label: t('nav.files') },
    { to: adminUsersPath(), label: t('nav.users') },
    { to: adminRequestsPath(), label: t('nav.requests') },
  ]

  return (
    <nav aria-label={t('nav.label')} className="border-border flex gap-1 border-b">
      {sections.map((section) => (
        <NavLink
          key={section.to}
          to={section.to}
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
