import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { myTableDetailPath } from '@/config/paths'
import { GameTableCard, useMyTables } from '@/features/tables'

/**
 * /my/tables - the tables the signed-in person plays at, as an accepted player.
 *
 * The cards link to `/my/tables/:id` and not to the public detail: from here the question is "how is
 * my table going?" - the calendar and my attendance - and not "should I apply?".
 */
export function MyTablesPage() {
  const { t } = useTranslation('tables')
  // isLoadingError, no isError: un refetch de fondo que falla no debe tapar una lista ya
  // cargada (docs/decisiones.md #150).
  const { data, isPending, isLoadingError, refetch } = useMyTables()

  return (
    <div className="space-y-4">
      <h1 className="font-serif text-2xl font-semibold">{t('myTables.title')}</h1>
      {isPending && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }, (_, index) => (
            <Skeleton key={index} className="h-40 rounded-lg" />
          ))}
        </div>
      )}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && <EmptyState title={t('myTables.emptyTitle')} description={t('myTables.emptyDescription')} />}
      {data && data.content.length > 0 && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {data.content.map((table) => (
            <GameTableCard key={table.id} table={table} linkTo={myTableDetailPath(table.id)} />
          ))}
        </div>
      )}
    </div>
  )
}

export { MyTablesPage as Component }
