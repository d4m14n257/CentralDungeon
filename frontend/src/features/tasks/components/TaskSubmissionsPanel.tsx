import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { RichTextView } from '@/components/RichTextView'
import { Skeleton } from '@/components/ui/skeleton'
import { browserTimeZone, formatDateTime } from '@/lib/date'

import { useTaskSubmissions } from '../api/useTaskSubmissions'
import type { SubmittedFile } from '../types'

export interface TaskSubmissionsPanelProps {
  /** The task whose answers to show. */
  taskId: string
  /**
   * How to render the files of one answer.
   *
   * A render prop because the file list belongs to `features/files` and a feature never imports from
   * another: the screen composing the two is where they meet (regla dura 16, §3.1.5).
   */
  renderFiles: (files: SubmittedFile[]) => ReactNode
}

/**
 * What came in for one task, and who has not answered.
 *
 * **Answers accumulate and none replaces another** (#76), so this is a history read forwards rather
 * than "the latest version": somebody who sent three drafts appears three times, in order, and the
 * master decides which one they meant. There is no accept and no reject, because judging an answer
 * takes a criterion the software does not have.
 *
 * **The missing list is a list of people to talk to** (#70). It offers no action, and that is
 * deliberate: not answering blocks nothing and removes nobody, so an "expulsar" button here would be
 * the interface promising something the rules refuse.
 *
 * Its query runs on mount, which is when the row was expanded: `CollapsibleSection` does not render
 * its children while closed, so a board of fifteen tasks costs one request and not fifteen.
 *
 * @param props.taskId      the task
 * @param props.renderFiles how to render the files of an answer
 */
export function TaskSubmissionsPanel({ taskId, renderFiles }: TaskSubmissionsPanelProps) {
  const { t, i18n } = useTranslation('tasks')
  // isLoadingError, not isError: see docs/decisiones.md #150.
  const { data, isPending, isLoadingError, refetch } = useTaskSubmissions(taskId)
  const timeZone = browserTimeZone()

  if (isPending) {
    return <Skeleton className="h-24 w-full" />
  }

  if (isLoadingError || !data) {
    return <ErrorState onRetry={() => void refetch()} />
  }

  return (
    <div className="space-y-6">
      <section className="space-y-2">
        <h4 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('submissions.title')}</h4>
        {data.submissions.length === 0 ? (
          <EmptyState title={t('submissions.emptyTitle')} description={t('submissions.emptyDescription')} />
        ) : (
          <ul className="divide-border divide-y">
            {data.submissions.map((submission) => (
              <li key={submission.submissionId} className="space-y-2 py-3">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <span className="text-sm font-medium">{submission.userName}</span>
                  {submission.submittedAt && (
                    <span className="text-fg-muted text-xs">{formatDateTime(submission.submittedAt, i18n.language, timeZone)}</span>
                  )}
                </div>
                {submission.content && <RichTextView html={submission.content} />}
                {submission.files.length > 0 && renderFiles(submission.files)}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-2">
        <h4 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">
          {t('submissions.missingTitle', { missing: data.missing.length, total: data.recipientCount })}
        </h4>
        {data.missing.length === 0 ? (
          <p className="text-fg-muted text-sm">{t('submissions.nobodyMissing')}</p>
        ) : (
          <>
            <ul className="flex flex-wrap gap-2">
              {data.missing.map((recipient) => (
                <li key={recipient.userId} className="border-border bg-raised rounded-md border px-2 py-1 text-xs">
                  {recipient.userName}
                </li>
              ))}
            </ul>
            {/* #70 stated where it is easiest to forget: this list is information, not a sanction. */}
            <p className="text-fg-muted text-xs">{t('submissions.missingHint')}</p>
          </>
        )}
      </section>
    </div>
  )
}
