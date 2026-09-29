import { useTranslation } from 'react-i18next'

import { masterDashboardPath, masterTablesPath } from '@/config/paths'
import { SectionNav, type SectionNavItem } from '@/components/SectionNav'

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

  const sections: SectionNavItem[] = [
    { to: masterDashboardPath(), label: t('nav.dashboard'), end: true },
    { to: masterTablesPath(), label: t('nav.tables'), end: false },
  ]

  return <SectionNav label={t('nav.label')} sections={sections} />
}
