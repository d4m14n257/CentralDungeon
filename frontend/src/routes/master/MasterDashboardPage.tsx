import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { EmptyState } from '@/components/EmptyState'
import { QueryErrorState } from '@/components/QueryErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { PageHeader } from '@/components/PageHeader'
import { masterTablesPath } from '@/config/paths'
import { MasterWorkItemList, useMasterDashboard } from '@/features/tables'

/**
 * `/master` — the work tray: what needs an answer today, across every table the reader runs (ADR-20 §4).
 *
 * **A tray, not a summary with numbers.** A master with three tables does not need to be told they
 * have twelve candidates, they need to know who they owe an answer to. Nothing here is a figure
 * that leaves the reader with the same question they arrived with.
 *
 * **The empty state is good news.** "Nothing is waiting for you" is the answer, not a failure, and
 * skill `diseno` §5 names this screen as the one where that is easiest to get wrong.
 */
export function MasterDashboardPage() {
  const { t } = useTranslation('master')
  // isLoadingError, not isError: a background refetch that fails must not blank a tray that already
  // loaded (ADR-19 §4).
  const { data, isPending, error, isLoadingError, refetch } = useMasterDashboard()

  // The header is there in every state: the screen keeps its shape while it loads and when it fails.
  const header = <PageHeader title={t('dashboard.title')} description={t('dashboard.subtitle')} help="masters.dashboard" />

  if (isPending) {
    return (
      <div className="space-y-4">
        {header}
        <Skeleton className="h-48 w-full" />
      </div>
    )
  }

  if (isLoadingError || !data) {
    return (
      <div className="space-y-4">
        {header}
        <QueryErrorState error={error} onRetry={() => void refetch()} />
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {header}

      {data.items.length === 0 ? (
        <EmptyState
          title={t('dashboard.emptyTitle')}
          description={t('dashboard.emptyDescription')}
          action={
            <Button asChild size="sm" variant="secondary">
              <Link to={masterTablesPath()}>{t('dashboard.emptyAction')}</Link>
            </Button>
          }
        />
      ) : (
        <MasterWorkItemList items={data.items} />
      )}

      <p className="text-fg-subtle text-xs">{t('dashboard.noReservationHint')}</p>
    </div>
  )
}

export { MasterDashboardPage as Component }
