import { useTranslation } from 'react-i18next'

import { PageHeader } from '@/components/PageHeader'

/**
 * `/admin` — the home of the Admin context (#270), and where an administrator lands on entering.
 *
 * **A welcome and nothing else, on purpose.** Until this existed the prefix painted the layout
 * around an empty outlet, open to anybody with a session. A dashboard of metrics was the obvious
 * filler, but nobody has decided which numbers an admin needs to open with, and a page of numbers
 * nobody reads is worse than a greeting. When that decision exists it replaces this body; the route
 * and the place in the nav are already the home's.
 *
 * It fetches nothing, so it has no loading, empty or error state to cover. Who may see it is
 * `AdminLayout`'s guard (#269).
 */
export function AdminHomePage() {
  const { t } = useTranslation('admin')

  return (
    <div className="space-y-2">
      <PageHeader title={t('home.title')} description={t('home.description')} />
    </div>
  )
}

export { AdminHomePage as Component }
