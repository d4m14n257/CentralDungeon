import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import type { TaskStatus } from '../types'

/**
 * Colour is never the only carrier of information (`frontend-diseno.md` §3): always a dot plus a
 * label. The class names are complete and static on purpose — Tailwind 4 scans the source for
 * literals and cannot see a class name built out of a template string.
 */
const STATE_CLASSES: Record<TaskStatus, { badge: string; dot: string }> = {
  Open: { badge: 'bg-state-open-bg text-state-open-fg', dot: 'bg-state-open-dot' },
  Closed: { badge: 'bg-state-done-bg text-state-done-fg', dot: 'bg-state-done-dot' },
}

/**
 * Whether a task is still taking answers. Its variants come from a `Record` over `TaskStatus`, so a
 * new status cannot be added without deciding how it looks (§3.2 regla 9).
 *
 * Closed reads as *done*, not as *cancelled*: what was handed in is still there and still readable
 * (#76), so a red badge would say something untrue about it.
 *
 * @param props.status the task's status
 */
export function TaskStatusBadge({ status }: { status: TaskStatus }) {
  const { t } = useTranslation('tasks')
  const classes = STATE_CLASSES[status]

  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium', classes.badge)}>
      <span className={cn('size-1.5 rounded-full', classes.dot)} />
      {t(`status.${status}`)}
    </span>
  )
}
