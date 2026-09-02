import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadMore } from '@/components/LoadMore'
import { Skeleton } from '@/components/ui/skeleton'
import { useMyApplications } from '@/features/registrations'
import { GameTableCard, useGameTables } from '@/features/tables'

export function TableListPage() {
  const { t } = useTranslation('tables')
  // isLoadingError, no isError: un refetch de fondo que falla no debe tapar una lista que ya
  // se cargó bien - TanStack Query pone isError en true igual, sin borrar el data cacheado
  // (docs/decisiones.md #150). El indicador global de conexión ya avisa que algo anda mal.
  const { data, isPending, isLoadingError, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useGameTables()
  // Cruce de dominios: la ruta compone, GameTableCard no importa de features/registrations
  // (regla dura 16). "Rejected" no cuenta como aplicada - ahí sí tiene sentido volver a postularse.
  const { data: myApplications } = useMyApplications()
  const appliedTableIds = new Set(
    myApplications?.content.filter((registration) => registration.status !== 'Rejected').map((registration) => registration.gameTableId),
  )

  // El explorador pagina con "Ver más" (#173): las páginas ya traídas se acumulan y se muestran juntas.
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
