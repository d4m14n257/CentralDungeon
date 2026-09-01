import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { masterTableDetailPath } from '@/config/paths'
import { GameTableCard, useManagedTables } from '@/features/tables'

export function MasterTablesPage() {
  const { t } = useTranslation('master')
  // isLoadingError, no isError: un refetch de fondo que falla no debe tapar una lista ya
  // cargada (docs/decisiones.md #150).
  const { data, isPending, isLoadingError, refetch } = useManagedTables()

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
      {data && data.content.length === 0 && (
        <EmptyState title={t('myTables.emptyTitle')} description={t('myTables.emptyDescription')} />
      )}
      {data && data.content.length > 0 && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {data.content.map((table) => (
            <GameTableCard key={table.id} table={table} linkTo={masterTableDetailPath(table.id)} />
          ))}
        </div>
      )}
    </div>
  )
}

export { MasterTablesPage as Component }
