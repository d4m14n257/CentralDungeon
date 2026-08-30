import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { GameTableCard, useGameTables } from '@/features/tables'

export function TableListPage() {
  const { t } = useTranslation('tables')
  const { data, isPending, isError, refetch } = useGameTables()

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
      {isError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && (
        <EmptyState title={t('explorer.emptyTitle')} description={t('explorer.emptyDescription')} />
      )}
      {data && data.content.length > 0 && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {data.content.map((table) => (
            <GameTableCard key={table.id} table={table} />
          ))}
        </div>
      )}
    </div>
  )
}

export { TableListPage as Component }
