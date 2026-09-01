import { useTranslation } from 'react-i18next'
import { Outlet, useParams } from 'react-router'
import { toast } from 'sonner'

import { useConfirm } from '@/components/ConfirmDialog'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { TableStatusBadge, useManagedTable, useOpenTable } from '@/features/tables'
import { ApiError } from '@/types/api'

export function MasterTableDetailPage() {
  const { t } = useTranslation('master')
  const { id } = useParams<{ id: string }>()
  const tableId = id ?? ''
  // useManagedTable, no useGameTable: el backend verifica pertenencia antes de leer nada y
  // devuelve 403 sin cuerpo si el actor no es master de esta mesa - useGameTable es el detalle
  // público que cualquier jugador consulta en /tables/:id (decisiones.md #152).
  const { data: table, isPending, error, isLoadingError } = useManagedTable(tableId)
  const openTable = useOpenTable(tableId)
  const confirm = useConfirm()

  if (isPending) {
    return <Skeleton className="h-32 w-full" />
  }

  if (error instanceof ApiError && error.status === 403) {
    return <ForbiddenState />
  }

  if (isLoadingError || !table) {
    return <ErrorState />
  }

  async function handleOpen() {
    const confirmed = await confirm({
      title: t('detail.openConfirmTitle'),
      description: t('detail.openConfirmDescription'),
    })
    if (!confirmed) return
    openTable.mutate(undefined, { onSuccess: () => toast.success(t('detail.openSuccess')) })
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <h1 className="font-serif text-2xl font-semibold">{table.name}</h1>
        <div className="flex items-center gap-2">
          <TableStatusBadge status={table.status} />
          {table.status === 'Preparation' && (
            <Button size="sm" onClick={() => void handleOpen()} disabled={openTable.isPending}>
              {t('detail.open')}
            </Button>
          )}
        </div>
      </div>
      <Outlet context={{ tableId, maxPlayers: table.maxPlayers, playerCount: table.playerCount }} />
    </div>
  )
}

export { MasterTableDetailPage as Component }
