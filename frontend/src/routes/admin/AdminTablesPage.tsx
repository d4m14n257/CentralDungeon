import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { useConfirm } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { PaginationControls } from '@/components/PaginationControls'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useDisclosure } from '@/hooks/useDisclosure'
import {
  CreateUnassignedTableDialog,
  JustifiedTableActionDialog,
  TableStatusBadge,
  useAdminTables,
  useApproveTable,
  useRequestChanges,
  type AdminTableSummary,
} from '@/features/tables'
import { ApiError } from '@/types/api'

import { AssignMastersDialog } from './AssignMastersDialog'

function AdminTableRow({ table }: { table: AdminTableSummary }) {
  const { t } = useTranslation('admin')
  const confirm = useConfirm()
  const approveTable = useApproveTable()
  const requestChanges = useRequestChanges()
  const assignDialog = useDisclosure()
  const requestChangesDialog = useDisclosure()

  async function handleApprove() {
    const confirmed = await confirm({ title: t('tables.approveConfirmTitle'), description: t('tables.approveConfirmDescription') })
    if (!confirmed) return
    approveTable.mutate(table.id, { onSuccess: () => toast.success(t('tables.approveSuccess')) })
  }

  return (
    <li className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
      <div className="min-w-0 space-y-1">
        <p className="truncate font-medium">{table.name}</p>
        <div className="flex flex-wrap items-center gap-2">
          <TableStatusBadge status={table.status} />
          <span className="text-fg-muted text-xs">{table.primaryMasterName ?? t('tables.noPrimaryMaster')}</span>
        </div>
      </div>
      <div className="flex flex-wrap gap-2">
        {table.status === 'Unassigned' && (
          <Button size="sm" onClick={() => assignDialog.open()}>
            {t('tables.assignMasters')}
          </Button>
        )}
        {table.status === 'Preparation' && (
          <>
            <Button size="sm" onClick={() => void handleApprove()} disabled={approveTable.isPending}>
              {t('tables.approve')}
            </Button>
            <Button size="sm" variant="outline" onClick={() => requestChangesDialog.open()}>
              {t('tables.requestChanges')}
            </Button>
          </>
        )}
      </div>
      <AssignMastersDialog tableId={table.id} tableName={table.name} open={assignDialog.isOpen} onOpenChange={assignDialog.close} />
      <JustifiedTableActionDialog
        open={requestChangesDialog.isOpen}
        onOpenChange={requestChangesDialog.close}
        title={t('tables.requestChangesDialogTitle', { name: table.name })}
        description={t('tables.requestChangesDialogDescription')}
        submitLabel={t('tables.requestChanges')}
        isPending={requestChanges.isPending}
        onConfirm={(justification) => {
          requestChanges.mutate(
            { tableId: table.id, request: { justification } },
            {
              onSuccess: () => {
                toast.success(t('tables.requestChangesSuccess'))
                requestChangesDialog.close()
              },
            },
          )
        }}
      />
    </li>
  )
}

export function AdminTablesPage() {
  const { t } = useTranslation('admin')
  const createDialog = useDisclosure()
  // Lista de trabajo: se pagina con número de página y total a la vista, no con "Ver más" (#173).
  const [page, setPage] = useState(0)
  // isLoadingError, no isError: ver docs/decisiones.md #150.
  const { data, isPending, isLoadingError, error, refetch } = useAdminTables(undefined, page)

  if (error instanceof ApiError && error.status === 403) {
    return <ForbiddenState />
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <h1 className="font-serif text-2xl font-semibold">{t('tables.title')}</h1>
        <Button size="sm" variant="outline" onClick={() => createDialog.open()}>
          {t('tables.createUnassigned')}
        </Button>
      </div>
      {isPending && <Skeleton className="h-40 w-full" />}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && <EmptyState title={t('tables.emptyTitle')} description={t('tables.emptyDescription')} />}
      {data && data.content.length > 0 && (
        <>
          <ul className="divide-border divide-y rounded-lg border">
            {data.content.map((table) => (
              <AdminTableRow key={table.id} table={table} />
            ))}
          </ul>
          <PaginationControls page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPageChange={setPage} />
        </>
      )}
      <CreateUnassignedTableDialog open={createDialog.isOpen} onOpenChange={createDialog.close} />
    </div>
  )
}

export { AdminTablesPage as Component }
