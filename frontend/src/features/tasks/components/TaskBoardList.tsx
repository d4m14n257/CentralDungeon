import { LockIcon, PencilIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { CollapsibleSection } from '@/components/CollapsibleSection'
import { IconAction } from '@/components/IconAction'
import { RichTextView } from '@/components/RichTextView'
import { browserTimeZone, formatDateTime } from '@/lib/date'

import { TaskAudienceBadge } from './TaskAudienceBadge'
import { TaskStatusBadge } from './TaskStatusBadge'
import { TaskSubmissionsPanel } from './TaskSubmissionsPanel'
import type { SubmittedFile, TableTask } from '../types'

export interface TaskBoardListProps {
  /** The table's tasks, oldest first. */
  tasks: TableTask[]
  /** Called with the task to correct. */
  onEdit: (task: TableTask) => void
  /** Called with the task whose intake should end. */
  onClose: (task: TableTask) => void
  /** Whether a mutation is in flight, so the row actions can say so. */
  isBusy: boolean
  /** How to render the files of an answer — see `TaskSubmissionsPanel` for why it is a prop. */
  renderFiles: (files: SubmittedFile[]) => ReactNode
}

/**
 * The master's board: every request the table has made, each opening onto what came back.
 *
 * **Collapsed, each row already answers the question it exists for** — "three of five" — so a master
 * with a dozen requests does not have to open all of them to find the one still waiting. That is the
 * whole reason `CollapsibleSection` takes a summary.
 *
 * The answers are only fetched when a row is opened: a board of fifteen would otherwise be fifteen
 * requests for panels nobody looked at.
 *
 * @param props.tasks       the table's tasks, oldest first
 * @param props.onEdit      called with the task to correct
 * @param props.onClose     called with the task whose intake should end
 * @param props.isBusy      whether a mutation is in flight
 * @param props.renderFiles how to render the files of an answer
 */
export function TaskBoardList({ tasks, onEdit, onClose, isBusy, renderFiles }: TaskBoardListProps) {
  const { t, i18n } = useTranslation('tasks')
  const timeZone = browserTimeZone()

  return (
    <ul className="space-y-3">
      {tasks.map((task) => (
        <li key={task.taskId}>
          <CollapsibleSection
            title={task.title}
            summary={
              <span className="flex flex-wrap items-center gap-2">
                <TaskAudienceBadge audience={task.audience} targetUserName={task.targetUserName} />
                <TaskStatusBadge status={task.status} />
                <span className="text-fg-muted text-xs">
                  {t('board.answered', { people: task.respondentCount, total: task.recipientCount })}
                </span>
                {task.isMandatory && <span className="text-fg-muted text-xs">{t('board.mandatory')}</span>}
              </span>
            }
            actions={
              <>
                <IconAction
                  label={t('board.edit')}
                  icon={<PencilIcon className="size-4" />}
                  disabled={isBusy}
                  onClick={() => onEdit(task)}
                />
                {task.status === 'Open' && (
                  <IconAction
                    label={t('board.close')}
                    icon={<LockIcon className="size-4" />}
                    disabled={isBusy}
                    onClick={() => onClose(task)}
                  />
                )}
              </>
            }
          >
            {/* Everything below is mounted only once the row is expanded — CollapsibleSection does
                not render closed children — which is what makes the answers load on demand without
                anybody having to track which rows are open. */}
            <div className="space-y-4">
              {task.description && <RichTextView html={task.description} />}
              <dl className="text-fg-muted grid gap-1 text-xs">
                <div className="flex gap-2">
                  <dt>{t('board.answersWith')}</dt>
                  <dd>{answerChannels(task, t)}</dd>
                </div>
                {task.dueAt && (
                  <div className="flex gap-2">
                    <dt>{t('board.due')}</dt>
                    <dd>{formatDateTime(task.dueAt, i18n.language, timeZone)}</dd>
                  </div>
                )}
                {task.sessionSequenceNumber !== null && (
                  <div className="flex gap-2">
                    <dt>{t('board.session')}</dt>
                    <dd>{t('form.sessionOption', { number: task.sessionSequenceNumber })}</dd>
                  </div>
                )}
              </dl>
              <TaskSubmissionsPanel taskId={task.taskId} renderFiles={renderFiles} />
            </div>
          </CollapsibleSection>
        </li>
      ))}
    </ul>
  )
}

/** Which channels the request takes, as one sentence rather than two checkboxes read back. */
function answerChannels(task: TableTask, t: (key: string) => string): string {
  if (task.acceptsText && task.acceptsFiles) return t('board.channels.both')
  return task.acceptsText ? t('board.channels.text') : t('board.channels.files')
}
