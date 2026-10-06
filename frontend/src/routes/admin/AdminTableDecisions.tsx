import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { adminQueueErrorKey } from '@/features/adminQueue'
import { HelpLink } from '@/features/help'
import {
  JustifiedTableActionDialog,
  useApproveTable,
  useCancelTable,
  useRequestChanges,
  type GameTableDetail,
  type GameTableStatus,
} from '@/features/tables'
import { useConfirm } from '@/hooks/useConfirm'
import { useDisclosure } from '@/hooks/useDisclosure'

/**
 * The statuses an admin may cancel a table from, here: every one the backend's `CANCELABLE_STATUSES`
 * admits but `Unassigned`, which nobody ever saw and is removed instead (#175) - the list's own
 * action for it.
 */
const ADMIN_CANCELABLE: readonly GameTableStatus[] = ['Preparation', 'ChangesRequested', 'Opened', 'InProgress', 'PauseRequested', 'Pause']

/**
 * Whether {@link AdminTableDecisions} has anything to offer for a table in this status.
 *
 * @param status where the table stands
 * @returns true when there is a decision to take: reviewing it, or cancelling it
 */
export function hasAdminDecisions(status: GameTableStatus): boolean {
  return status === 'Preparation' || ADMIN_CANCELABLE.includes(status)
}

/** What {@link AdminTableDecisions} takes. */
export interface AdminTableDecisionsProps {
  /** The table, as its admin view loaded it. */
  table: Pick<GameTableDetail, 'id' | 'name' | 'status'>
  /**
   * Which half to draw: the review's decisions (`safe`) or the cancellation (`destructive`). The status
   * notice draws each in its own place, the destructive one apart at the right (#288).
   */
  only: 'safe' | 'destructive'
}

/**
 * The decisions an admin takes about a table from its own status tab (#286): **approve it** or **send
 * it back with the reason** while it waits for review, and **cancel it with the reason** when it has
 * no fix.
 *
 * **Reviewing from the table, not only from the tray.** #176 moved approving and requesting changes
 * to `/admin/queue` so two screens would not offer the same decision under different rules. They do
 * not: these are the same endpoints, and the same reservation rule answers both (#100) - a table
 * another admin took from the tray is refused here with the colleague's name, exactly as it is there.
 * The tray stays the work list; this is the decision taken by whoever already read the whole table.
 *
 * Drawn inside the table's status notice (#287), with the rest of what changes where the table stands.
 * Text buttons and not icons: these are the screen's decisions, not the actions of a row (#272).
 * Every one is confirmed with what follows (#283), and the two with a reason ask for it in a dialog;
 * the master reads it in the table's history and is told by the bell (#244, #284).
 *
 * @param props.table the table
 * @param props.only  the review's decisions, or the cancellation
 */
export function AdminTableDecisions({ table, only }: AdminTableDecisionsProps) {
  const { t } = useTranslation('admin')
  const confirm = useConfirm()
  const approve = useApproveTable()
  const requestChanges = useRequestChanges()
  const cancel = useCancelTable(table.id)
  const changesDialog = useDisclosure()
  const cancelDialog = useDisclosure()

  const inReview = only === 'safe' && table.status === 'Preparation'
  const canCancel = only === 'destructive' && ADMIN_CANCELABLE.includes(table.status)
  const changesError = adminQueueErrorKey(requestChanges.error)

  async function handleApprove() {
    const confirmed = await confirm({
      title: t('tables.decisions.approveConfirmTitle', { name: table.name }),
      description: t('tables.decisions.approveConfirmDescription'),
      confirmLabel: t('tables.decisions.approve'),
    })
    if (!confirmed) return
    approve.mutate(table.id, {
      onSuccess: () => toast.success(t('tables.decisions.approveSuccess')),
      // The mutation shows its own errors (it is the tray's too): the one worth a sentence is a
      // colleague holding the review (#100).
      onError: (failure) => toast.error(t(adminQueueErrorKey(failure) ?? 'queue.errors.generic')),
    })
  }

  // A fragment and not a wrapper: the buttons join the status notice's own row, next to the other
  // actions on the table (#287).
  return (
    <>
      {inReview && (
        <>
          <Button type="button" size="sm" onClick={() => void handleApprove()} disabled={approve.isPending}>
            {t('tables.decisions.approve')}
          </Button>
          <Button type="button" size="sm" variant="outline" onClick={() => changesDialog.open()}>
            {t('tables.decisions.requestChanges')}
          </Button>
        </>
      )}
      {canCancel && (
        <Button type="button" size="sm" variant="destructive" onClick={() => cancelDialog.open()}>
          {t('tables.decisions.cancel')}
        </Button>
      )}

      <JustifiedTableActionDialog
        open={changesDialog.isOpen}
        onOpenChange={(open) => !open && changesDialog.close()}
        title={t('tables.decisions.requestChangesTitle', { name: table.name })}
        description={t('tables.decisions.requestChangesDescription')}
        submitLabel={t('tables.decisions.requestChanges')}
        review={{
          title: t('tables.decisions.requestChangesConfirmTitle', { name: table.name }),
          description: t('tables.decisions.requestChangesConfirmDescription'),
        }}
        isPending={requestChanges.isPending}
        errorMessage={requestChanges.error ? t(changesError ?? 'queue.errors.generic') : null}
        help={
          <p className="text-fg-subtle text-xs">
            <HelpLink section="admins.reviewing">{t('tables.decisions.reviewHelpLink')}</HelpLink>
          </p>
        }
        onConfirm={(justification) =>
          requestChanges.mutate(
            { tableId: table.id, request: { justification } },
            {
              onSuccess: () => {
                toast.success(t('tables.decisions.requestChangesSuccess'))
                changesDialog.close()
              },
            },
          )
        }
      />

      <JustifiedTableActionDialog
        open={cancelDialog.isOpen}
        onOpenChange={(open) => !open && cancelDialog.close()}
        title={t('tables.decisions.cancelTitle', { name: table.name })}
        description={t('tables.decisions.cancelDescription')}
        submitLabel={t('tables.decisions.cancel')}
        destructive
        review={{
          title: t('tables.decisions.cancelConfirmTitle', { name: table.name }),
          description: t('tables.decisions.cancelConfirmDescription'),
        }}
        isPending={cancel.isPending}
        onConfirm={(justification) =>
          cancel.mutate(
            { justification },
            {
              onSuccess: () => {
                toast.success(t('tables.decisions.cancelSuccess'))
                cancelDialog.close()
              },
            },
          )
        }
      />
    </>
  )
}
