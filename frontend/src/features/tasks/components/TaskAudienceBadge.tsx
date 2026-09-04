import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import type { TaskAudience } from '../types'

/**
 * Which of the three groups a task is addressed to (#63), as one dot and one label.
 *
 * **The colour is never the only carrier of the information** (`frontend-diseno.md` §3): dot plus
 * label, always. The classes are written out in full and statically, because Tailwind 4 scans the
 * source and never sees a class it had to concatenate to exist — the same reason `TableStatusBadge`
 * spells its own out.
 */
const AUDIENCE_DOT: Record<TaskAudience, string> = {
  Candidates: 'bg-state-pending-dot',
  Players: 'bg-state-open-dot',
  Single: 'bg-state-paused-dot',
}

export interface TaskAudienceBadgeProps {
  /** Who the task is addressed to. */
  audience: TaskAudience
  /** The name of the one person addressed, when the audience is `Single`. */
  targetUserName?: string | null
}

/**
 * Names the audience of a task, and the person when there is only one.
 *
 * A `Single` task reads as "para Ana" rather than as "Individual": the label exists so a master
 * scanning their board can tell at a glance which request is whose, and a category name would make
 * them open it to find out.
 *
 * @param props.audience       who the task is addressed to
 * @param props.targetUserName the person, when the audience is `Single`
 */
export function TaskAudienceBadge({ audience, targetUserName }: TaskAudienceBadgeProps) {
  const { t } = useTranslation('tasks')
  const label = audience === 'Single' && targetUserName ? t('audience.singleNamed', { name: targetUserName }) : t(`audience.${audience}`)

  return (
    <span className="text-fg-muted inline-flex items-center gap-1.5 text-xs">
      <span className={cn('size-1.5 rounded-full', AUDIENCE_DOT[audience])} aria-hidden="true" />
      {label}
    </span>
  )
}
