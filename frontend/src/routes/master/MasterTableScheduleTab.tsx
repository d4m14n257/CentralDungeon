import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useOutletContext } from 'react-router'

import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'
import { masterTableEditPath } from '@/config/paths'
import { browserTimeZone, formatDate, formatSlot, utcSlotToLocal } from '@/lib/date'
import type { GameTableStatus, TableScheduleEntry } from '@/features/tables'

/** The two states where the backend still accepts a rewrite of the table (#189). */
const EDITABLE_STATUSES: GameTableStatus[] = ['Preparation', 'ChangesRequested']

interface OutletContext {
  tableId: string
  status: GameTableStatus
  isPrimary: boolean
  schedule: TableScheduleEntry[]
  startDate: string | null
  duration: string | null
  totalSessions: number | null
}

/**
 * The agenda tab of `/master/tables/:id` — the weekly shape of the table, plus when it starts, how
 * long a session runs and how many there are.
 *
 * **Read-only, and shown in the reader's own time** (#22): the agenda travels in UTC and is
 * converted once, here, through `lib/date.ts`. Editing it is the same act as editing the table —
 * the agenda is replaced as a set, not row by row (#190) — so this tab links to the form rather
 * than growing a second way to write the same rows.
 */
export function MasterTableScheduleTab() {
  const { t, i18n } = useTranslation('master')
  const { tableId, status, isPrimary, schedule, startDate, duration, totalSessions } = useOutletContext<OutletContext>()
  // #22 took `users.timezone` out of the model, so the browser is the only source today (#111, #192).
  const timeZone = useMemo(() => browserTimeZone(), [])

  const canEdit = isPrimary && EDITABLE_STATUSES.includes(status)

  return (
    <div className="space-y-4">
      <dl className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <div>
          <dt className="text-fg-subtle text-xs">{t('schedule.startDate')}</dt>
          <dd className="text-sm">{startDate ? formatDate(startDate, i18n.language, timeZone) : t('schedule.notSet')}</dd>
        </div>
        <div>
          <dt className="text-fg-subtle text-xs">{t('schedule.duration')}</dt>
          <dd className="text-sm">{duration ? duration.slice(0, 5) : t('schedule.notSet')}</dd>
        </div>
        <div>
          <dt className="text-fg-subtle text-xs">{t('schedule.totalSessions')}</dt>
          <dd className="text-sm">{totalSessions ?? t('schedule.notSet')}</dd>
        </div>
      </dl>

      {schedule.length === 0 ? (
        <EmptyState title={t('schedule.emptyTitle')} description={t('schedule.emptyDescription')} />
      ) : (
        <ul className="divide-border divide-y rounded-lg border">
          {schedule.map((entry) => (
            <li key={`${entry.weekday}-${entry.hourtime}`} className="px-4 py-2 text-sm">
              {formatSlot(utcSlotToLocal({ weekday: entry.weekday, hourtime: entry.hourtime }, timeZone), i18n.language, duration)}
            </li>
          ))}
        </ul>
      )}

      {canEdit && (
        <Button asChild size="sm">
          <Link to={masterTableEditPath(tableId)}>{t('schedule.edit')}</Link>
        </Button>
      )}
      {!canEdit && <p className="text-fg-muted text-sm">{t('schedule.lockedHint')}</p>}
    </div>
  )
}

export { MasterTableScheduleTab as Component }
