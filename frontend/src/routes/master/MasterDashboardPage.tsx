import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { helpPath, masterTablesPath } from '@/config/paths'
import { MasterWorkItemList, useMasterDashboard } from '@/features/tables'
import { ApiError } from '@/types/api'

/**
 * `/master` — the work tray: what needs an answer today, across every table the reader runs (#136).
 *
 * **A tray, not a summary with numbers.** A master with three tables does not need to be told they
 * have twelve candidates, they need to know who they owe an answer to. Nothing here is a figure
 * that leaves the reader with the same question they arrived with.
 *
 * **The empty state is good news.** "Nothing is waiting for you" is the answer, not a failure, and
 * `frontend-diseno.md` §5 names this screen as the one where that is easiest to get wrong.
 */
export function MasterDashboardPage() {
  const { t } = useTranslation('master')
  // isLoadingError, not isError: a background refetch that fails must not blank a tray that already
  // loaded (#150).
  const { data, isPending, error, isLoadingError, refetch } = useMasterDashboard()

  if (isPending) {
    return <Skeleton className="h-48 w-full" />
  }

  if (error instanceof ApiError && error.status === 403) {
    return <ForbiddenState />
  }

  if (isLoadingError || !data) {
    return <ErrorState onRetry={() => void refetch()} />
  }

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h1 className="font-serif text-2xl font-semibold">{t('dashboard.title')}</h1>
        <p className="text-fg-muted text-sm">{t('dashboard.subtitle')}</p>
      </div>

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

      <p className="text-fg-subtle text-xs">
        {t('dashboard.noReservationHint')}{' '}
        {/* To the exact #ref and not to the whole help page: that is what makes opening it worth it (#168). */}
        <Link to={helpPath('masters', 'dashboard')} className="underline underline-offset-2">
          {t('dashboard.helpLink')}
        </Link>
      </p>
    </div>
  )
}

export { MasterDashboardPage as Component }
