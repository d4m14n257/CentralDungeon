import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'

import { ApplicableTaskList } from './ApplicableTaskList'
import { TaskSubmitDialog } from './TaskSubmitDialog'
import type { CommitStagedFiles, StagedFile } from '@/types/file'
import { useApplicableTasks } from '../api/useApplicableTasks'
import { useSubmitTask } from '../api/useSubmitTask'
import type { ApplicableTask, SubmittedFile } from '../types'

export interface TableTasksSectionProps {
  /**
   * The table. **An id and nothing else** (§3.1.5): this block knows no `GameTable` type, which is
   * what keeps `features/tasks` from depending on `features/tables`.
   */
  tableId: string
  /**
   * The "how this works" link, rendered by the screen.
   *
   * A render prop for the same reason `renderFiles` is one: the help lives in `features/help` and a
   * feature never imports from another (regla dura 16, §3.1.5). The screen names the section.
   */
  renderHelpLink: () => ReactNode
  /**
   * How to render the files of an answer, and how to render the file picker.
   *
   * Render props because both belong to `features/files` and a feature never imports from another.
   * The screens in `src/routes/` are the one place domains are composed (regla dura 16, §3.1.5).
   */
  renderFiles: (files: SubmittedFile[]) => ReactNode
  renderFilePicker: (onPick: (file: StagedFile) => void) => ReactNode
  /**
   * Uploads what was staged and hands back the ids (#238).
   *
   * A prop and not an import, for the same reason `renderFilePicker` is one: this feature may never
   * import `features/files` (regla dura 16), and the screen that composes both is the one that wires
   * them. Nothing has reached the server until this runs.
   */
  commitFiles: CommitStagedFiles
}

/**
 * What this table asks of the person reading it — the block `/player/tables/:id` and `/player/my-tables/:id` both
 * mount.
 *
 * It runs its own query and owns its own dialog, taking only a `tableId` from the screen around it
 * (§3.1.5). That is also why it is one component and not two copies of the same JSX in two routes:
 * the two screens ask the same question, and the answer differs by who is asking, not by where they
 * are asking from.
 *
 * A candidate on the public detail sees what will be asked of them before they apply (#206); a
 * player sees what is asked of players and whatever was addressed to them in particular (#76).
 *
 * @param props.tableId          the table
 * @param props.helpAudience     which help page to link to
 * @param props.renderFiles      how to render the files of an answer
 * @param props.renderFilePicker how to render the file picker inside the answer dialog
 */
export function TableTasksSection({ tableId, renderHelpLink, renderFiles, renderFilePicker, commitFiles }: TableTasksSectionProps) {
  const { t } = useTranslation('tasks')
  // isLoadingError, not isError: see docs/decisiones.md #150.
  const { data: tasks, isPending, isLoadingError, refetch } = useApplicableTasks(tableId)
  const submit = useSubmitTask(tableId)
  const [answering, setAnswering] = useState<ApplicableTask | null>(null)

  return (
    <section className="space-y-2">
      <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('applicable.title')}</h2>

      {isPending ? (
        <Skeleton className="h-24 w-full" />
      ) : isLoadingError || !tasks ? (
        <ErrorState onRetry={() => void refetch()} />
      ) : tasks.length === 0 ? (
        <EmptyState title={t('applicable.emptyTitle')} description={t('applicable.emptyDescription')} />
      ) : (
        <ApplicableTaskList tasks={tasks} onAnswer={setAnswering} renderFiles={renderFiles} />
      )}

      {renderHelpLink()}

      {answering && (
        <TaskSubmitDialog
          open
          onOpenChange={(open) => !open && setAnswering(null)}
          task={answering}
          isBusy={submit.isPending}
          renderFilePicker={renderFilePicker}
          onSubmit={async (draft) => {
            // Upload first, send second (#238). A file that failed does not hold the answer back:
            // answers accumulate (#76), so completing it is sending another one - and the person is
            // told which file to try again with.
            const { fileIds, failed } = await commitFiles(draft.staged)
            if (failed.length > 0) {
              toast.error(t('submit.someFilesFailed', { names: failed.join(', ') }))
            }
            submit.mutate(
              { taskId: answering.taskId, input: { content: draft.content, fileIds } },
              {
                onSuccess: () => {
                  setAnswering(null)
                  if (failed.length === 0) toast.success(t('submit.sentToast'))
                },
              },
            )
          }}
        />
      )}
    </section>
  )
}
