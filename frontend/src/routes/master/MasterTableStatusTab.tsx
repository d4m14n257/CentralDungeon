import { useTranslation } from 'react-i18next'
import { useNavigate, useOutletContext } from 'react-router'
import { toast } from 'sonner'

import { useConfirm } from '@/hooks/useConfirm'
import { ErrorState } from '@/components/ErrorState'
import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useDisclosure } from '@/hooks/useDisclosure'
import { masterTablesPath } from '@/config/paths'
import { HelpLink } from '@/features/help'
import {
  JustifiedTableActionDialog,
  useCancelTable,
  useDeleteTable,
  useFinishTable,
  pauseRequestErrorKey,
  useRequestTablePause,
  useResubmitTable,
  useSubmitTableForReview,
  useStartTable,
  useTableStatusHistory,
  type GameTableStatus,
} from '@/features/tables'

/**
 * Mirror of `GameTableService.CANCELABLE_STATUSES`.
 *
 * **`PauseRequested` is on it since F3.4**, and it had to be: giving the status a producer meant a
 * table could now sit in it, and without this entry a master who asked for a pause and then decided
 * to close the table instead would find the button gone — with no way back except waiting for an
 * admin to answer a request that no longer matters. A table waiting on an answer is still a table
 * its master may end.
 */
const CANCELABLE_STATUSES: GameTableStatus[] = ['Preparation', 'ChangesRequested', 'Opened', 'InProgress', 'PauseRequested', 'Pause']
/** Only what was never public is deleted; everything else is cancelled and stays in the history (#175). */
const DELETABLE_STATUSES: GameTableStatus[] = ['Draft', 'Preparation', 'ChangesRequested']

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
  const submitForReview = useSubmitTableForReview(tableId)
  const start = useStartTable(tableId)
  const finish = useFinishTable(tableId)
  const cancel = useCancelTable(tableId)
  const remove = useDeleteTable(tableId)
  const cancelDialog = useDisclosure()
  const navigate = useNavigate()

  if (!isPrimary) {
    return null
  }

  /**
   * Sending the draft is what makes the table exist for anybody else (#245), and what takes it out of
   * the master's hands until an admin answers — so it is confirmed, like every other one-way step.
   */
  async function handleSubmitForReview() {
    const confirmed = await confirm({ title: t('status.submitConfirmTitle'), description: t('status.submitConfirmDescription') })
    if (!confirmed) return
    submitForReview.mutate(undefined, { onSuccess: () => toast.success(t('status.submitSuccess')) })
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
      {status === 'Draft' && (
        <Button size="sm" onClick={() => void handleSubmitForReview()} disabled={submitForReview.isPending}>
          {t('status.submit')}
        </Button>
      )}
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

/**
 * Asking an admin to pause the table (#32, F3.4).
 *
 * **Its own block, outside `StatusActions`, and that placement is the rule made visible.** Everything
 * in `StatusActions` belongs to the `Primary` alone — starting, finishing, cancelling, deleting — so
 * that component refuses to render for anybody else. Asking for a pause is not one of those: the
 * backend takes it from **any** master of the table, because a co-master running the sessions is
 * exactly the person who knows the table has to stop. Folding it into the Primary-only block would
 * have hidden it from them for no reason anybody wrote down.
 *
 * **Asking is not pausing**, and the two states say so in different words: while the request waits,
 * the table still promises its dates and the sessions still appear, because only an admin's answer
 * freezes the agenda. A master who read "pausada" here and stopped turning up would be acting on a
 * pause that had not happened.
 */
function PauseRequest({ tableId, status }: Pick<OutletContext, 'tableId' | 'status'>) {
  const { t } = useTranslation('master')
  const requestPause = useRequestTablePause(tableId)
  const dialog = useDisclosure()

  // Only from a table that is actually running: there is nothing to pause before it started, and
  // `Pause`/`PauseRequested` are already on the other side of this act (principio 2).
  if (status !== 'InProgress' && status !== 'PauseRequested') {
    return null
  }

  if (status === 'PauseRequested') {
    // No button, and the sentence that replaces it says what is missing and who owes it.
    return <p className="text-fg-muted text-sm">{t('status.pauseRequestedHint')}</p>
  }

  // Which is exactly why the refusal below is reachable: this screen hides the button once the
  // table is in `PauseRequested`, so the only press that can be refused comes from a stale tab.
  const pauseError = pauseRequestErrorKey(requestPause.error)

  return (
    <div className="space-y-2">
      <Button size="sm" variant="outline" onClick={() => dialog.open()} disabled={requestPause.isPending}>
        {t('status.requestPause')}
      </Button>
      <JustifiedTableActionDialog
        open={dialog.isOpen}
        onOpenChange={dialog.close}
        title={t('status.requestPauseDialogTitle')}
        description={t('status.requestPauseDialogDescription')}
        submitLabel={t('status.requestPause')}
        isPending={requestPause.isPending}
        errorMessage={pauseError === null ? null : t(pauseError)}
        help={
          <p className="text-fg-subtle text-xs">
            {t('status.requestPauseHint')} <HelpLink section="masters.pause">{t('status.requestPauseHelpLink')}</HelpLink>
          </p>
        }
        onConfirm={(justification) => {
          requestPause.mutate(
            { justification },
            {
              onSuccess: () => {
                toast.success(t('status.requestPauseSuccess'))
                dialog.close()
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
      <PauseRequest tableId={context.tableId} status={context.status} />
      {/* The two halves of the wait, said out loud: a draft nobody has seen yet, and one an admin is
          already reading — which is also why the Edit button is gone in the second (#245). */}
      {context.status === 'Draft' && <p className="text-fg-muted text-sm">{t('status.draftHint')}</p>}
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
