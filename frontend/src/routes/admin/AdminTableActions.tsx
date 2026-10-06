import { Pause, Play, Trash2, UserPlus } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { IconAction } from '@/components/IconAction'
import { Button } from '@/components/ui/button'
import { HelpLink } from '@/features/help'
import {
  JustifiedTableActionDialog,
  tableActionErrorMessage,
  useDeleteTable,
  usePauseTable,
  useResumeTable,
  type AdminTableSummary,
} from '@/features/tables'
import { useConfirm } from '@/hooks/useConfirm'
import { useDisclosure } from '@/hooks/useDisclosure'

import { AssignMastersDialog } from './AssignMastersDialog'

/** What {@link AdminTableActions} needs of a table: who it is and where it stands. */
export interface AdminTableActionsProps {
  /** The table, from the admin listing or from its detail - the three fields both have. */
  table: Pick<AdminTableSummary, 'id' | 'name' | 'status'>
  /**
   * What to do once the table is removed. The list needs nothing - the row simply goes - but the
   * table's own detail would be left showing something that no longer exists.
   */
  onDeleted?: (() => void) | undefined
  /**
   * How the actions are drawn. `row` (the default) is a list row's: icons with their tooltip (#272).
   * `notice` is the table's own status notice (#287): buttons with their text, because there they are
   * the screen's decisions and not the actions of a row.
   */
  presentation?: 'row' | 'notice'
  /**
   * Which actions to draw. Absent draws them all, as a list row does; the status notice draws the
   * `safe` ones with the rest and the `destructive` one (remove) apart at the right (#288).
   */
  only?: 'safe' | 'destructive' | undefined
}

/** One of the actions, drawn the way the place that shows it asks for. */
function ActionControl({
  presentation,
  icon,
  label,
  onClick,
  disabled,
  destructive,
}: {
  presentation: 'row' | 'notice'
  icon: ReactNode
  label: string
  onClick: () => void
  disabled?: boolean
  destructive?: boolean
}) {
  if (presentation === 'notice') {
    return (
      <Button type="button" size="sm" variant={destructive ? 'destructive' : 'outline'} onClick={onClick} disabled={disabled ?? false}>
        {label}
      </Button>
    )
  }
  return (
    <IconAction
      icon={icon}
      label={label}
      onClick={onClick}
      disabled={disabled ?? false}
      className={destructive ? 'text-destructive hover:text-destructive' : undefined}
    />
  )
}

/**
 * The statuses in which {@link AdminTableActions} offers anything at all, so a screen can say so
 * instead of drawing an empty row: assigning or removing (`Unassigned`), pausing (`InProgress`) and
 * resuming (`Pause`).
 */
export const STATUSES_WITH_ADMIN_ACTIONS: readonly AdminTableSummary['status'][] = ['Unassigned', 'InProgress', 'Pause']

/**
 * The admin's own actions on one table, offered only where its status admits them: assigning masters
 * and removing a table nobody runs, pausing a running one, resuming a paused one.
 *
 * **One component for the two places that offer them** (#284, #287): a row of `/admin/tables`, as icons,
 * and the status notice above the tabs of `/admin/tables/:id`, as buttons. It was the list's private row component until the detail arrived; two
 * copies would be two places for "which actions, in which status" to drift apart. Every one of them
 * tells the table's masters (#284), which the backend does.
 *
 * A component of its own rather than a closure in the column list, because deleting is per-table state
 * — `useDeleteTable` is keyed by the table's id — and hoisting it to the screen would mean one
 * mutation shared by every row.
 *
 * **Approving and requesting changes are not here any more** (#176, F3.3). They moved to
 * `/admin/queue` with the work they belong to, and they moved rather than being copied: a table
 * sitting in `Preparation` is work waiting on somebody, and work waiting on somebody is the tray's.
 * Two screens offering the same decision under different rules is exactly what the move removes.
 */
export function AdminTableActions({ table, onDeleted, presentation = 'row', only }: AdminTableActionsProps) {
  const showSafe = only !== 'destructive'
  const showDestructive = only !== 'safe'
  const { t } = useTranslation('admin')
  const confirm = useConfirm()
  const removeTable = useDeleteTable(table.id)
  const pauseTable = usePauseTable()
  const resumeTable = useResumeTable()
  const assignDialog = useDisclosure()
  const pauseDialog = useDisclosure()

  // A table with no master was never public: it is deleted, not cancelled (decisiones.md #175).
  async function handleDelete() {
    const confirmed = await confirm({ title: t('tables.deleteConfirmTitle'), description: t('tables.deleteConfirmDescription') })
    if (!confirmed) return
    removeTable.mutate(undefined, {
      onSuccess: () => {
        toast.success(t('tables.deleteSuccess'))
        onDeleted?.()
      },
    })
  }

  /**
   * Bringing a paused table back (#33, #163, #193).
   *
   * **A confirmation and not a form**, because resuming carries no justification: #32 asks for a
   * reason when a table stops and resuming is the return to normal. What the confirmation is for is
   * the consequence nobody would guess — the pending sessions are rescheduled from today, so every
   * date the players had in their calendar moves.
   *
   * **And therefore the refusal is a toast**: the confirmation has already closed on the press and
   * there is no form left to put a message over. It is written from the code, naming the table the
   * agenda now collides with (#193, #197) — "no pudimos completar la acción" would throw away the
   * one fact that makes it solvable.
   */
  async function handleResume() {
    const confirmed = await confirm({
      title: t('tables.resumeConfirmTitle', { name: table.name }),
      description: t('tables.resumeConfirmDescription'),
    })
    if (!confirmed) return
    resumeTable.mutate(table.id, {
      onSuccess: () => toast.success(t('tables.resumeSuccess')),
      onError: (failure) => {
        const message = tableActionErrorMessage(failure)
        if (message !== null) toast.error(t(message.key, message.params))
      },
    })
  }

  const pauseError = tableActionErrorMessage(pauseTable.error)

  return (
    <>
      {/* Only on a table with no master: everything else already has one, and the two acts are about
          giving it one or admitting it will never have one (principio 2). */}
      {table.status === 'Unassigned' && (
        <>
          {showSafe && (
            <ActionControl
              presentation={presentation}
              icon={<UserPlus className="size-4" />}
              label={t('tables.assignMasters')}
              onClick={() => assignDialog.open()}
            />
          )}
          {showDestructive && (
            <ActionControl
              presentation={presentation}
              icon={<Trash2 className="size-4" />}
              label={t('tables.delete')}
              onClick={() => void handleDelete()}
              disabled={removeTable.isPending}
              destructive
            />
          )}
        </>
      )}
      {/* The two halves of #163, which have had an endpoint since E2 and no screen at all until now.
          They live here rather than in the tray because neither is work waiting on anybody: pausing
          a running table is a decision an admin takes about a table they went looking for, and this
          is the screen that lists every table there is (#176). What *does* wait on somebody — a
          master's request for a pause — arrives in `/admin/queue` as a request, and approving it
          there is what moves the table to `Pause`. */}
      {showSafe && table.status === 'InProgress' && (
        <ActionControl
          presentation={presentation}
          icon={<Pause className="size-4" />}
          label={t('tables.pause')}
          onClick={() => pauseDialog.open()}
          disabled={pauseTable.isPending}
        />
      )}
      {showSafe && table.status === 'Pause' && (
        <ActionControl
          presentation={presentation}
          icon={<Play className="size-4" />}
          label={t('tables.resume')}
          onClick={() => void handleResume()}
          disabled={resumeTable.isPending}
        />
      )}
      <AssignMastersDialog tableId={table.id} tableName={table.name} open={assignDialog.isOpen} onOpenChange={assignDialog.close} />
      {/* Pausing does carry a reason (#32), and it is the master who will read it: the table stops
          promising dates to their players and they were not the ones who decided it. */}
      <JustifiedTableActionDialog
        open={pauseDialog.isOpen}
        onOpenChange={(open) => !open && pauseDialog.close()}
        title={t('tables.pauseDialogTitle', { name: table.name })}
        description={t('tables.pauseDialogDescription')}
        submitLabel={t('tables.pause')}
        review={{ title: t('tables.pauseConfirmTitle', { name: table.name }), description: t('tables.pauseConfirmDescription') }}
        isPending={pauseTable.isPending}
        errorMessage={pauseError === null ? null : t(pauseError.key, pauseError.params)}
        help={
          <p className="text-fg-subtle text-xs">
            {t('tables.pauseHint')} <HelpLink section="admins.pausing">{t('tables.pauseHelpLink')}</HelpLink>
          </p>
        }
        onConfirm={(justification) => {
          pauseTable.mutate(
            { tableId: table.id, request: { justification } },
            {
              onSuccess: () => {
                toast.success(t('tables.pauseSuccess'))
                pauseDialog.close()
              },
            },
          )
        }}
      />
    </>
  )
}
