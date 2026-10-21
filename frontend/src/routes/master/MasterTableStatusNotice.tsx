import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'

import { StatusNotice } from '@/components/StatusNotice'
import { Button } from '@/components/ui/button'
import { masterTablesPath } from '@/config/paths'
import { HelpLink } from '@/features/help'
import {
  JustifiedTableActionDialog,
  pauseRequestErrorKey,
  useCancelTable,
  useDeleteTable,
  useFinishTable,
  useRequestTablePause,
  useResubmitTable,
  useStartTable,
  useSubmitTableForReview,
  type GameTableStatus,
} from '@/features/tables'
import { useConfirm } from '@/hooks/useConfirm'
import { useDisclosure } from '@/hooks/useDisclosure'

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
/** Only what was never public is deleted; everything else is cancelled and stays in the history (ADR-07 §6). */
const DELETABLE_STATUSES: GameTableStatus[] = ['Draft', 'Preparation', 'ChangesRequested']

/** What {@link MasterTableStatusNotice} takes. */
export interface MasterTableStatusNoticeProps {
  /** The table. */
  tableId: string
  /** Where it stands. */
  status: GameTableStatus
  /** Whether the reader is its Primary: every transition but asking for a pause is theirs alone. */
  isPrimary: boolean
}

/**
 * Where the table stands, said above its tabs, with every decision its masters can take about it right
 * there (ADR-21 §10): send it to review, resend it, start it, finish it, ask for a pause, cancel it or
 * delete it. The master's twin of `AdminTableStatusNotice`.
 *
 * **It replaced the "Status" tab**, which mixed deciding with reading and put the decisions at the end of
 * the last tab. The history stayed behind as a tab of its own (`MasterTableHistoryTab`).
 *
 * **Asking for a pause is any master's; the rest is the Primary's.** The backend takes a pause request
 * from a co-master, because the one running the sessions is exactly who knows the table has to stop —
 * and **asking is not pausing**: while the request waits the table still promises its dates, which is
 * what the `PauseRequested` sentence says.
 *
 * Every status has its sentence, including the ones with nothing to decide, so the notice never
 * disappears and the reader learns where to look.
 *
 * @param props.tableId   the table
 * @param props.status    where it stands
 * @param props.isPrimary whether the reader is its Primary
 */
export function MasterTableStatusNotice({ tableId, status, isPrimary }: MasterTableStatusNoticeProps) {
  const { t } = useTranslation('master')
  const confirm = useConfirm()
  const navigate = useNavigate()
  const submitForReview = useSubmitTableForReview(tableId)
  const resubmit = useResubmitTable(tableId)
  const start = useStartTable(tableId)
  const finish = useFinishTable(tableId)
  const cancel = useCancelTable(tableId)
  const remove = useDeleteTable(tableId)
  const requestPause = useRequestTablePause(tableId)
  const cancelDialog = useDisclosure()
  const pauseDialog = useDisclosure()

  /** Every one-way step is confirmed first, saying what follows (principle 3 of diseno §1). */
  async function confirmThen(key: 'submit' | 'resubmit' | 'start' | 'finish', mutate: (onSuccess: () => void) => void) {
    const confirmed = await confirm({ title: t(`status.${key}ConfirmTitle`), description: t(`status.${key}ConfirmDescription`) })
    if (!confirmed) return
    mutate(() => toast.success(t(`status.${key}Success`)))
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

  const safe: ReactNode[] = []
  if (isPrimary && status === 'Draft') {
    safe.push(
      <Button
        key="submit"
        size="sm"
        disabled={submitForReview.isPending}
        onClick={() => void confirmThen('submit', (onSuccess) => submitForReview.mutate(undefined, { onSuccess }))}
      >
        {t('status.submit')}
      </Button>,
    )
  }
  if (isPrimary && status === 'ChangesRequested') {
    safe.push(
      <Button
        key="resubmit"
        size="sm"
        disabled={resubmit.isPending}
        onClick={() => void confirmThen('resubmit', (onSuccess) => resubmit.mutate(undefined, { onSuccess }))}
      >
        {t('status.resubmit')}
      </Button>,
    )
  }
  if (isPrimary && status === 'Opened') {
    safe.push(
      <Button
        key="start"
        size="sm"
        disabled={start.isPending}
        onClick={() => void confirmThen('start', (onSuccess) => start.mutate(undefined, { onSuccess }))}
      >
        {t('status.start')}
      </Button>,
    )
  }
  if (isPrimary && status === 'InProgress') {
    safe.push(
      <Button
        key="finish"
        size="sm"
        disabled={finish.isPending}
        onClick={() => void confirmThen('finish', (onSuccess) => finish.mutate(undefined, { onSuccess }))}
      >
        {t('status.finish')}
      </Button>,
    )
  }
  // Only from a table that is actually running: there is nothing to pause before it started, and
  // `PauseRequested` is already on the other side of this act (principle 2).
  if (status === 'InProgress') {
    safe.push(
      <Button key="pause" size="sm" variant="outline" disabled={requestPause.isPending} onClick={() => pauseDialog.open()}>
        {t('status.requestPause')}
      </Button>,
    )
  }

  const destructive: ReactNode[] = []
  if (isPrimary && CANCELABLE_STATUSES.includes(status)) {
    destructive.push(
      <Button key="cancel" size="sm" variant="destructive" onClick={() => cancelDialog.open()}>
        {t('status.cancel')}
      </Button>,
    )
  }
  if (isPrimary && DELETABLE_STATUSES.includes(status)) {
    destructive.push(
      <Button key="delete" size="sm" variant="outline" disabled={remove.isPending} onClick={() => void handleDelete()}>
        {t('status.delete')}
      </Button>,
    )
  }

  // Reachable only from a stale tab: the button is gone once the table is in `PauseRequested`.
  const pauseError = pauseRequestErrorKey(requestPause.error)
  const hasSafe = safe.length > 0
  const hasDestructive = destructive.length > 0

  // Cancelling and deleting go apart at the right when there are other buttons to keep them away from
  // (ADR-21 §10); alone, they stay where a button is read first.
  return (
    <>
      <StatusNotice
        title={t(`notice.${status}.title`)}
        description={t(`notice.${status}.description`)}
        actions={hasSafe ? safe : hasDestructive ? destructive : undefined}
        destructiveActions={hasSafe && hasDestructive ? destructive : undefined}
      />
      <JustifiedTableActionDialog
        open={cancelDialog.isOpen}
        onOpenChange={cancelDialog.close}
        title={t('status.cancelDialogTitle')}
        description={t('status.cancelDialogDescription')}
        submitLabel={t('status.cancel')}
        review={{ title: t('status.cancelConfirmTitle'), description: t('status.cancelConfirmDescription') }}
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
      <JustifiedTableActionDialog
        open={pauseDialog.isOpen}
        onOpenChange={pauseDialog.close}
        title={t('status.requestPauseDialogTitle')}
        description={t('status.requestPauseDialogDescription')}
        submitLabel={t('status.requestPause')}
        review={{ title: t('status.requestPauseConfirmTitle'), description: t('status.requestPauseConfirmDescription') }}
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
                pauseDialog.close()
              },
            },
          )
        }}
      />
    </>
  )
}
