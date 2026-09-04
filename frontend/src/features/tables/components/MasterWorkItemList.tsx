import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { Button } from '@/components/ui/button'
import {
  masterTableDetailPath,
  masterTableEditPath,
  masterTableSessionsPath,
  masterTableStatusPath,
  masterTableTasksPath,
} from '@/config/paths'
import { browserTimeZone, formatDateTime } from '@/lib/date'

import type { MasterWorkItem, MasterWorkItemKind } from '../types'

/**
 * Where each kind of work is actually resolved. A tray row that only names the problem makes the
 * reader go find the screen themselves, which is the work it was supposed to save them.
 */
const DESTINATION: Record<MasterWorkItemKind, (tableId: string) => string> = {
  CandidatesWaiting: masterTableDetailPath,
  OverdueTaskMissing: masterTableTasksPath,
  SessionToRecord: masterTableSessionsPath,
  ChangesRequested: masterTableEditPath,
  ReadyToStart: masterTableStatusPath,
}

interface MasterWorkItemListProps {
  /** What is waiting, already ordered by the server: longest wait first. */
  items: MasterWorkItem[]
}

/**
 * The master's work tray, rendered (#136).
 *
 * It receives the items and does not fetch them: the screen composes. The phrasing is built here
 * from the `kind` and its numbers, because the backend sends a code and never a sentence (#197).
 *
 * @param props.items what is waiting, longest wait first
 */
export function MasterWorkItemList({ items }: MasterWorkItemListProps) {
  const { t, i18n } = useTranslation('master')
  // #22 took `users.timezone` out of the model, so the browser is the only source today; `lib/date.ts`
  // takes the zone as a parameter precisely so a profile preference would change this line and no
  // other (#111, #192).
  const timeZone = useMemo(() => browserTimeZone(), [])

  return (
    <ul className="divide-border divide-y rounded-lg border">
      {items.map((item) => (
        <li key={`${item.tableId}-${item.kind}-${item.subject ?? ''}`} className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3">
          <span className="min-w-0 flex-1">
            <span className="block truncate font-serif font-semibold">{item.tableName}</span>
            <span className="text-fg-muted block text-sm">
              {t(`dashboard.item.${item.kind}`, { count: item.count, subject: item.subject ?? '' })}
            </span>
          </span>
          <span className="text-fg-subtle shrink-0 text-xs">
            {t('dashboard.waitingSince', { since: formatDateTime(item.since, i18n.language, timeZone) })}
          </span>
          <Button asChild size="sm" variant="secondary" className="shrink-0">
            <Link to={DESTINATION[item.kind](item.tableId)}>{t('dashboard.resolve')}</Link>
          </Button>
        </li>
      ))}
    </ul>
  )
}
