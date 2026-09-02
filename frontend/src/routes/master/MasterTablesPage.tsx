import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { masterTableDetailPath, masterTableNewPath } from '@/config/paths'
import { GameTableCard, useManagedTables } from '@/features/tables'
import { useMe } from '@/features/users'

export function MasterTablesPage() {
  const { t } = useTranslation('master')
  // isLoadingError, no isError: un refetch de fondo que falla no debe tapar una lista ya
  // cargada (docs/decisiones.md #150).
  const { data, isPending, isLoadingError, refetch } = useManagedTables()
  const { data: me } = useMe()
  // Dirigir no es crear (decisiones.md #135): un master de una sola mesa asignado sin el rol de
  // plataforma ve esta lista, pero no el botón de crear una mesa propia.
  const canCreate = me?.roles.includes('Master') ?? false

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <h1 className="font-serif text-2xl font-semibold">{t('myTables.title')}</h1>
        {canCreate && (
          <Button asChild size="sm">
            <Link to={masterTableNewPath()}>{t('myTables.createNew')}</Link>
          </Button>
        )}
      </div>
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
            <GameTableCard key={table.id} table={table} linkTo={masterTableDetailPath(table.id)} />
          ))}
        </div>
      )}
    </div>
  )
}

export { MasterTablesPage as Component }
