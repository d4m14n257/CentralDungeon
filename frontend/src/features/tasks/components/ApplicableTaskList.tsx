import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { CollapsibleSection } from '@/components/CollapsibleSection'
import { RichTextView } from '@/components/RichTextView'
import { Button } from '@/components/ui/button'
import { browserTimeZone, formatDateTime } from '@/lib/date'

import { MySubmissions } from './MySubmissions'
import { TaskAudienceBadge } from './TaskAudienceBadge'
import type { ApplicableTask, SubmittedFile } from '../types'

export interface ApplicableTaskListProps {
  /** What this table asks of the reader, oldest first. */
  tasks: ApplicableTask[]
  /** Called with the task the reader wants to answer. */
  onAnswer: (task: ApplicableTask) => void
  /** How to render the files of an answer — see `TaskSubmissionsPanel` for why it is a prop. */
  renderFiles: (files: SubmittedFile[]) => ReactNode
}

/**
 * What a table is asking of the person reading it, on `/tables/:id` and `/my/tables/:id`.
 *
 * **A request you cannot answer yet still says why.** Somebody who has not applied sees what will be
 * asked of them - which is half of deciding whether to apply (#206) - with a line explaining that
 * answering comes after applying, rather than a button that is simply missing (principio 2 de
 * `frontend-diseno.md` §1).
 *
 * **Mandatory is shown as a label and never as a threat** (#70): the copy says the master considers
 * it important, and nothing on this screen suggests that missing it costs a seat, because it does not.
 *
 * @param props.tasks       what the table asks of the reader
 * @param props.onAnswer    called with the task they want to answer
 * @param props.renderFiles how to render the files of an answer
 */
export function ApplicableTaskList({ tasks, onAnswer, renderFiles }: ApplicableTaskListProps) {
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
                <TaskAudienceBadge audience={task.audience} />
                {task.isMandatory && <span className="text-fg-muted text-xs">{t('board.mandatory')}</span>}
                {task.mySubmissionCount > 0 && (
                  <span className="text-fg-muted text-xs">{t('applicable.answered', { count: task.mySubmissionCount })}</span>
                )}
              </span>
            }
          >
            <div className="space-y-4">
              {task.description && <RichTextView html={task.description} />}

              <dl className="text-fg-muted grid gap-1 text-xs">
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

              <MySubmissions taskId={task.taskId} renderFiles={renderFiles} />

              {task.canSubmit ? (
                <Button type="button" size="sm" onClick={() => onAnswer(task)}>
                  {task.mySubmissionCount > 0 ? t('applicable.answerAgain') : t('applicable.answer')}
                </Button>
              ) : (
                <p className="text-fg-muted text-xs">{t('applicable.cannotAnswer')}</p>
              )}
            </div>
          </CollapsibleSection>
        </li>
      ))}
    </ul>
  )
}
