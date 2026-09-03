import { useTranslation } from 'react-i18next'
import { useNavigate, useOutletContext } from 'react-router'
import { toast } from 'sonner'

import { useConfirm } from '@/components/ConfirmDialog'
import { ErrorState } from '@/components/ErrorState'
import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useDisclosure } from '@/hooks/useDisclosure'
import { masterTablesPath } from '@/config/paths'
import {
  JustifiedTableActionDialog,
  useCancelTable,
  useDeleteTable,
  useFinishTable,
  useResubmitTable,
  useStartTable,
  useTableStatusHistory,
  type GameTableStatus,
} from '@/features/tables'

const CANCELABLE_STATUSES: GameTableStatus[] = ['Preparation', 'ChangesRequested', 'Opened', 'InProgress', 'Pause']
/** Solo lo que nunca fue público se borra; lo demás se cancela y queda en el historial (#175). */
const DELETABLE_STATUSES: GameTableStatus[] = ['Preparation', 'ChangesRequested']

interface OutletContext {
  tableId: string
  status: GameTableStatus
  isPrimary: boolean
}

function StatusTimeline({ tableId }: { tableId: string }) {
  const { t, i18n } = useTranslation('master')
  const { data, isPending, isLoadingError, refetch } = useTableStatusHistory(tableId)

  if (isPending) {
    return <Skeleton className="h-24 w-full" />
  }
  if (isLoadingError) {
    return <ErrorState onRetry={() => void refetch()} />
  }
  if (data.length === 0) {
    return <EmptyState title={t('status.historyEmptyTitle')} />
  }

  return (
    <ol className="divide-border divide-y rounded-lg border">
      {data.map((change) => (
        <li key={change.id} className="space-y-1 px-4 py-3 text-sm">
          <div className="flex items-center justify-between gap-4">
            <span>
              {t(`status.${change.fromStatus}` as const, { ns: 'tables', defaultValue: change.fromStatus })} →{' '}
              {t(`status.${change.toStatus}` as const, { ns: 'tables', defaultValue: change.toStatus })}
            </span>
            <span className="text-fg-subtle text-xs">{new Date(change.createdAt).toLocaleString(i18n.language)}</span>
          </div>
          <p className="text-fg-muted text-xs">{t('status.changedBy', { name: change.changedByName })}</p>
          {change.justification && <p className="text-fg text-xs italic">"{change.justification}"</p>}
        </li>
      ))}
    </ol>
  )
}

function StatusActions({ tableId, status, isPrimary }: OutletContext) {
  const { t } = useTranslation('master')
  const confirm = useConfirm()
  const resubmit = useResubmitTable(tableId)
  const start = useStartTable(tableId)
  const finish = useFinishTable(tableId)
  const cancel = useCancelTable(tableId)
  const remove = useDeleteTable(tableId)
  const cancelDialog = useDisclosure()
  const navigate = useNavigate()

  if (!isPrimary) {
    return null
  }

  async function handleResubmit() {
    const confirmed = await confirm({ title: t('status.resubmitConfirmTitle'), description: t('status.resubmitConfirmDescription') })
    if (!confirmed) return
    resubmit.mutate(undefined, { onSuccess: () => toast.success(t('status.resubmitSuccess')) })
  }

  async function handleStart() {
    const confirmed = await confirm({ title: t('status.startConfirmTitle'), description: t('status.startConfirmDescription') })
    if (!confirmed) return
    start.mutate(undefined, { onSuccess: () => toast.success(t('status.startSuccess')) })
  }

  async function handleFinish() {
    const confirmed = await confirm({ title: t('status.finishConfirmTitle'), description: t('status.finishConfirmDescription') })
    if (!confirmed) return
    finish.mutate(undefined, { onSuccess: () => toast.success(t('status.finishSuccess')) })
  }

  async function handleDelete() {
    const confirmed = await confirm({ title: t('status.deleteConfirmTitle'), description: t('status.deleteConfirmDescription') })
    if (!confirmed) return
    remove.mutate(undefined, {
      onSuccess: () => {
        toast.success(t('status.deleteSuccess'))
        void navigate(masterTablesPath())
      },
    })
  }

  return (
    <div className="flex flex-wrap gap-2">
      {status === 'ChangesRequested' && (
        <Button size="sm" onClick={() => void handleResubmit()} disabled={resubmit.isPending}>
          {t('status.resubmit')}
        </Button>
      )}
      {status === 'Opened' && (
        <Button size="sm" onClick={() => void handleStart()} disabled={start.isPending}>
          {t('status.start')}
        </Button>
      )}
      {status === 'InProgress' && (
        <Button size="sm" onClick={() => void handleFinish()} disabled={finish.isPending}>
          {t('status.finish')}
        </Button>
      )}
      {CANCELABLE_STATUSES.includes(status) && (
        <Button size="sm" variant="destructive" onClick={() => cancelDialog.open()}>
          {t('status.cancel')}
        </Button>
      )}
      {DELETABLE_STATUSES.includes(status) && (
        <Button size="sm" variant="outline" onClick={() => void handleDelete()} disabled={remove.isPending}>
          {t('status.delete')}
        </Button>
      )}
      <JustifiedTableActionDialog
        open={cancelDialog.isOpen}
        onOpenChange={cancelDialog.close}
        title={t('status.cancelDialogTitle')}
        description={t('status.cancelDialogDescription')}
        submitLabel={t('status.cancel')}
        destructive
        isPending={cancel.isPending}
        onConfirm={(justification) => {
          cancel.mutate(
            { justification },
            {
              onSuccess: () => {
                toast.success(t('status.cancelSuccess'))
                cancelDialog.close()
              },
            },
          )
        }}
      />
    </div>
  )
}

function StatusPanel(context: OutletContext) {
  const { t } = useTranslation('master')

  return (
    <div className="space-y-4">
      <StatusActions {...context} />
      {context.status === 'Preparation' && <p className="text-fg-muted text-sm">{t('status.waitingForAdmin')}</p>}
      {context.status === 'Pause' && <p className="text-fg-muted text-sm">{t('status.pausedByAdmin')}</p>}
      <div className="space-y-2">
        <h2 className="text-sm font-medium">{t('status.historyTitle')}</h2>
        <StatusTimeline tableId={context.tableId} />
      </div>
    </div>
  )
}

/**
 * The status tab: where the table is in its lifecycle, the transitions available from here, and the
 * history with the reason behind each step.
 */
export function MasterTableStatusTab() {
  const context = useOutletContext<OutletContext>()
  return <StatusPanel {...context} />
}

export { MasterTableStatusTab as Component }
