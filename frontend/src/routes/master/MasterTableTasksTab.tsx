import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useOutletContext } from 'react-router'
import { toast } from 'sonner'

import { useConfirm } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { helpPath } from '@/config/paths'
import { FileList } from '@/features/files'
import { useTablePlayers } from '@/features/registrations'
import { useTableSessions } from '@/features/tables'
import {
  TaskBoardList,
  TaskFormDialog,
  useCloseTask,
  usePublishTask,
  useTableTasks,
  useUpdateTask,
  type CreateTaskInput,
  type TableTask,
} from '@/features/tasks'

interface OutletContext {
  tableId: string
}

/**
 * The **Peticiones** tab of /master/tables/:id — what the table asks of its people, and what came
 * back.
 *
 * Three things the screen has to keep true, because each one is a rule that reads as a missing
 * feature to anybody who does not know it:
 *
 * - **Publishing notifies** (#77). Creating and publishing are one act; there is no draft to save.
 *   Correcting afterwards deliberately does not notify again.
 * - **The system does not judge answers** (#76). There is no accept and no reject — answers pile up
 *   and the master reads them.
 * - **Missing an answer costs nothing** (#70). The roster of who has not answered offers no action,
 *   on purpose.
 *
 * A child route rather than local state (§3.1.6 regla 5), so the tab has its own URL, survives a
 * refresh and can be linked to.
 *
 * The picker of who to address and the list of sessions come from other features, so they are read
 * **here** and passed down as plain data: a feature never imports from another (regla dura 16).
 */
export function MasterTableTasksTab() {
  const { t } = useTranslation('tasks')
  const { tableId } = useOutletContext<OutletContext>()
  const confirm = useConfirm()

  // isLoadingError, not isError: see docs/decisiones.md #150.
  const { data: tasks, isPending, isLoadingError, refetch } = useTableTasks(tableId)
  const { data: players } = useTablePlayers(tableId)
  const { data: sessions } = useTableSessions(tableId)

  const publish = usePublishTask(tableId)
  const update = useUpdateTask(tableId)
  const close = useCloseTask(tableId)

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editing, setEditing] = useState<TableTask | null>(null)

  function openPublish() {
    setEditing(null)
    setIsFormOpen(true)
  }

  function openEdit(task: TableTask) {
    setEditing(task)
    setIsFormOpen(true)
  }

  function handleSubmit(input: CreateTaskInput) {
    if (editing) {
      update.mutate(
        { taskId: editing.taskId, input },
        {
          onSuccess: () => {
            setIsFormOpen(false)
            toast.success(t('form.savedToast'))
          },
        },
      )
      return
    }
    publish.mutate(input, {
      onSuccess: () => {
        setIsFormOpen(false)
        toast.success(t('form.publishedToast'))
      },
    })
  }

  async function handleClose(task: TableTask) {
    const confirmed = await confirm({ title: t('board.closeTitle'), description: t('board.closeDescription') })
    if (!confirmed) return
    close.mutate(task.taskId)
  }

  if (isPending) {
    return <Skeleton className="h-40 w-full" />
  }

  if (isLoadingError || !tasks) {
    return <ErrorState onRetry={() => void refetch()} />
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('board.title')}</h2>
        <Button type="button" size="sm" onClick={openPublish}>
          {t('board.publish')}
        </Button>
      </div>

      {tasks.length === 0 ? (
        <EmptyState title={t('board.emptyTitle')} description={t('board.emptyDescription')} />
      ) : (
        <TaskBoardList
          tasks={tasks}
          onEdit={openEdit}
          onClose={(task) => void handleClose(task)}
          isBusy={update.isPending || close.isPending}
          // The file list lives in `features/files`; this screen is where the two domains meet.
          renderFiles={(files) => <FileList files={files} />}
        />
      )}

      <Link to={helpPath('masters', 'tasks')} className="text-fg-muted inline-block text-xs underline">
        {t('board.helpLink')}
      </Link>

      <TaskFormDialog
        open={isFormOpen}
        onOpenChange={setIsFormOpen}
        task={editing}
        players={players ?? []}
        sessions={(sessions ?? []).map((session) => ({ id: session.id, sequenceNumber: session.sequenceNumber }))}
        isBusy={publish.isPending || update.isPending}
        onSubmit={handleSubmit}
      />
    </div>
  )
}

export { MasterTableTasksTab as Component }
