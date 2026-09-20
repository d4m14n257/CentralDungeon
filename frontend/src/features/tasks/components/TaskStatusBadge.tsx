import { useTranslation } from 'react-i18next'

import { StatusBadge, type StatusTone } from '@/components/StatusBadge'

import type { TaskStatus } from '../types'

/**
 * Which tone each state wears. **The map stays here and not in `StatusBadge`**: which of this
 * feature's states counts as "open" is a decision about this domain, and a shared component that knew
 * it would be the wrong kind of shared (`arquitectura.md` §3.1.2).
 */
const STATE_TONES: Record<TaskStatus, StatusTone> = {
  Open: 'open',
  Closed: 'done',
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
  return <StatusBadge tone={STATE_TONES[status]} label={t(`status.${status}`)} />
}
