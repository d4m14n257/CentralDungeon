import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { masterTableDetailPath, masterTableNewPath } from '@/config/paths'
import { GameTableCard, useManagedTables } from '@/features/tables'
import { useMe } from '@/features/users'

/**
 * /master/tables - every table the person runs, whatever its status, drafts included.
 *
 * Membership, not role (#135): a co-master without the `Master` role sees their table here.
 */
export function MasterTablesPage() {
  const { t } = useTranslation('master')
  // isLoadingError, not isError: a background refetch that fails must not hide a list that already
  // loaded (docs/decisiones.md #150).
  const { data, isPending, isLoadingError, refetch } = useManagedTables()
  const { data: me } = useMe()
  // Running is not creating (decisiones.md #135): somebody running a single table, assigned without
  // the platform role, sees this list but not the button to create a table of their own.
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
