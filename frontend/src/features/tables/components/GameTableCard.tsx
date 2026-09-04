import { AlertTriangle, Calendar, Check } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { Card } from '@/components/ui/card'
import { tableDetailPath } from '@/config/paths'
import { browserTimeZone, formatSlot, utcSlotToLocal } from '@/lib/date'

import type { GameTableSummary } from '../types'
import { TableStatusBadge } from './TableStatusBadge'

/**
 * The explorer's card (frontend-diseno.md 4). The status goes under the title rather than beside
 * it: it is the first thing read after the name, and this way it does not compete with it for
 * width. The footer separates what describes the table from what describes whoever runs it.
 *
 * `alreadyApplied` is a plain boolean and not the application's real status (Candidate/Player):
 * this feature cannot import from `features/registrations` (regla dura 16), and "you already
 * applied" is true whether it is still pending or you were accepted - nothing more is needed. What
 * crosses that data is the screen that composes them (`TableListPage`), not this card.
 *
 * The agenda is shown **in local time** (#22): what travels is UTC and the conversion belongs to
 * `lib/date.ts`. And when the table clashes with something the reader is already committed to, the
 * card warns with the reason in view (#178) - a warning that does not explain itself is as useless
 * as a grey button with no stated reason (principio 2 de frontend-diseno.md 1).
 */
export function GameTableCard({ table, linkTo, alreadyApplied }: { table: GameTableSummary; linkTo?: string; alreadyApplied?: boolean }) {
  const { t, i18n } = useTranslation('tables')

  return (
    <Link to={linkTo ?? tableDetailPath(table.id)} className="group block h-full">
      <Card className="border-border-strong group-hover:border-brand-fg h-full gap-2.5 p-5 transition-colors">
        <h2 className="font-serif text-lg leading-6 font-semibold">{table.name}</h2>
        <div className="flex flex-wrap items-center gap-1.5">
          <TableStatusBadge status={table.status} />
          {alreadyApplied && (
            <span className="bg-raised text-fg-muted inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium">
              <Check className="size-3" />
              {t('explorer.alreadyApplied')}
            </span>
          )}
        </div>
        {table.tableTypeName && <p className="text-fg-muted text-sm">{table.tableTypeName}</p>}
        {table.schedule.length > 0 && (
          <p className="text-fg-muted flex items-start gap-1.5 text-sm">
            <Calendar className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
            <span>
              {table.schedule.map((slot) => formatSlot(utcSlotToLocal(slot, browserTimeZone()), i18n.language, table.duration)).join(' · ')}
            </span>
          </p>
        )}
        {table.scheduleConflict && (
          <p className="text-state-canceled-fg flex items-start gap-1.5 text-xs">
            <AlertTriangle className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
            <span>{t('explorer.scheduleConflict')}</span>
          </p>
        )}
        <div className="border-border text-fg-muted mt-auto flex items-center justify-between gap-2 border-t pt-2.5 text-sm">
          <span className="shrink-0 whitespace-nowrap">
            {table.maxPlayers != null
              ? t('explorer.players', { current: table.playerCount, max: table.maxPlayers })
              : t('explorer.playersUnlimited', { current: table.playerCount })}
          </span>
          {/* If the name does not fit, the name is what gets cut, never the karma: the karma is the number being compared. */}
          <span className="flex min-w-0 items-center gap-1">
            <span className="truncate">{table.primaryMaster.name}</span>
            <span className="shrink-0">·</span>
            <span className="text-brand-fg shrink-0">{table.primaryMaster.karma.toLocaleString(i18n.language)}</span>
          </span>
        </div>
      </Card>
    </Link>
  )
}
