import { ArrowRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { adminQueuePath } from '@/config/paths'
import { useAdminQueue } from '@/features/adminQueue'

/**
 * `/admin` — the home of the Admin context (ADR-20 §3), and where an administrator lands on entering.
 *
 * **A welcome and a way into the tray, on purpose.** The tray is what an admin has to do next, so
 * the home links it with how many items wait. That count is not a metric: nobody has decided which
 * numbers an admin needs to open with, and when that decision exists it replaces the rest of this
 * body — the route, the place in the nav and the access to the tray stay the home's.
 *
 * **The count is the tray's own total**, read as a page of one: it is the same list, with the same
 * reservation rule — an item another admin took is not in it — so the number never promises more than
 * the tray will show. Who may see the page is `AdminLayout`'s guard (ADR-20 §2).
 */
export function AdminHomePage() {
  const { t } = useTranslation('admin')
  // isLoadingError, not isError: a background refetch that fails must not blank a count that loaded (ADR-19 §4).
  const { data, isPending, isLoadingError, refetch } = useAdminQueue(0, 1)

  return (
    <div className="space-y-4">
      <PageHeader title={t('home.title')} description={t('home.description')} />
      {isPending && <Skeleton className="h-24 w-full" />}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && (
        <Link to={adminQueuePath()} className="group block">
          <Card className="border-border-strong group-hover:border-brand-fg gap-1.5 p-5 transition-colors">
            <h2 className="section-title leading-6">{t('home.queueTitle')}</h2>
            <p className="text-fg-muted flex items-center justify-between gap-2 text-sm">
              <span>{data.totalElements === 0 ? t('home.queueEmpty') : t('home.queueCount', { count: data.totalElements })}</span>
              <ArrowRight className="size-4 shrink-0" aria-hidden="true" />
            </p>
          </Card>
        </Link>
      )}
    </div>
  )
}

export { AdminHomePage as Component }
