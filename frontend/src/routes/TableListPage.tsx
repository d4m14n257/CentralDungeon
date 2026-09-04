import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadMore } from '@/components/LoadMore'
import { Skeleton } from '@/components/ui/skeleton'
import { useMyApplications } from '@/features/registrations'
import { GameTableCard, useGameTables } from '@/features/tables'

/**
 * The explorer, `/` - the open tables anyone can apply to.
 *
 * A master never finds their own table here: a table has one set of people who play at it and a
 * disjoint set who run it (#154), and the backend filters by the actor rather than the screen
 * hiding rows after the fact.
 */
export function TableListPage() {
  const { t } = useTranslation('tables')
  // isLoadingError, not isError: a background refetch that fails must not hide a list that already
  // loaded fine - TanStack Query sets isError to true anyway, without dropping the cached data
  // (docs/decisiones.md #150). The global connection indicator already says something is wrong.
  const { data, isPending, isLoadingError, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useGameTables()
  // A crossing of domains: the screen composes them, GameTableCard does not import from
  // features/registrations (regla dura 16). "Rejected" does not count as applied - applying again is
  // exactly what makes sense there.
  const { data: myApplications } = useMyApplications()
  const appliedTableIds = new Set(
    myApplications?.content.filter((registration) => registration.status !== 'Rejected').map((registration) => registration.gameTableId),
  )

  // The explorer paginates with "See more" (#173): the pages already fetched accumulate and show together.
  const tables = data?.pages.flatMap((page) => page.content) ?? []
  const total = data?.pages[0]?.totalElements ?? 0

  return (
    <div className="space-y-4">
      <h1 className="font-serif text-2xl font-semibold">{t('explorer.title')}</h1>
      {isPending && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }, (_, index) => (
            <Skeleton key={index} className="h-40 rounded-lg" />
          ))}
        </div>
      )}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && tables.length === 0 && <EmptyState title={t('explorer.emptyTitle')} description={t('explorer.emptyDescription')} />}
      {tables.length > 0 && (
        <>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            {tables.map((table) => (
              <GameTableCard key={table.id} table={table} alreadyApplied={appliedTableIds.has(table.id)} />
            ))}
          </div>
          <LoadMore
            hasMore={hasNextPage}
            isLoading={isFetchingNextPage}
            onLoadMore={() => void fetchNextPage()}
            shown={tables.length}
            total={total}
          />
        </>
      )}
    </div>
  )
}

export { TableListPage as Component }
