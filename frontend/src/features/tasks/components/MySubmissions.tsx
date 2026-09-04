import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { ErrorState } from '@/components/ErrorState'
import { RichTextView } from '@/components/RichTextView'
import { Skeleton } from '@/components/ui/skeleton'
import { browserTimeZone, formatDateTime } from '@/lib/date'

import { useMySubmissions } from '../api/useMySubmissions'
import type { SubmittedFile } from '../types'

export interface MySubmissionsProps {
  /** The task whose answers of mine to show. */
  taskId: string
  /** How to render the files of an answer — see `TaskSubmissionsPanel` for why it is a prop. */
  renderFiles: (files: SubmittedFile[]) => ReactNode
}

/**
 * What **I** already handed in for one request, oldest first.
 *
 * A list and not "the latest one": answers accumulate and none replaces another (#76), so showing
 * only the last would quietly claim the earlier ones stopped counting. Reading them in order is also
 * how somebody remembers what they already said.
 *
 * Nothing is shown when there is nothing: an empty state under every unanswered request would be
 * noise on a screen that is mostly unanswered requests.
 *
 * @param props.taskId      the task
 * @param props.renderFiles how to render the files of an answer
 */
export function MySubmissions({ taskId, renderFiles }: MySubmissionsProps) {
  const { t, i18n } = useTranslation('tasks')
  // isLoadingError, not isError: see docs/decisiones.md #150.
  const { data, isPending, isLoadingError, refetch } = useMySubmissions(taskId)
  const timeZone = browserTimeZone()

  if (isPending) {
    return <Skeleton className="h-12 w-full" />
  }

  if (isLoadingError || !data) {
    return <ErrorState onRetry={() => void refetch()} />
  }

  if (data.length === 0) {
    return null
  }

  return (
    <section className="space-y-2">
      <h4 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('applicable.mineTitle')}</h4>
      <ul className="divide-border divide-y">
        {data.map((submission) => (
          <li key={submission.submissionId} className="space-y-2 py-2">
            {submission.submittedAt && (
              <p className="text-fg-muted text-xs">{formatDateTime(submission.submittedAt, i18n.language, timeZone)}</p>
            )}
            {submission.content && <RichTextView html={submission.content} />}
            {submission.files.length > 0 && renderFiles(submission.files)}
          </li>
        ))}
      </ul>
    </section>
  )
}
