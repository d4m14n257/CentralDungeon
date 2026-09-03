import { AlertTriangle, Calendar, Check } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { Card } from '@/components/ui/card'
import { tableDetailPath } from '@/config/paths'
import { browserTimeZone, formatSlot, utcSlotToLocal } from '@/lib/date'

import type { GameTableSummary } from '../types'
import { TableStatusBadge } from './TableStatusBadge'

/**
 * La ficha del explorador (frontend-diseno.md 4). El estado va debajo del título y no al lado:
 * es lo primero que se lee después del nombre, y así no compite con él por el ancho.
 * El pie separa lo que describe a la mesa de lo que describe a quien la dirige.
 *
 * `alreadyApplied` es un booleano plano, no el estado real de la postulación (Candidate/Player):
 * esta feature no puede importar de `features/registrations` (regla dura 16), y "ya te
 * postulaste" es verdad tanto si sigue pendiente como si ya te aceptaron - no hace falta más.
 * Quien cruza esa data es la ruta que compone (`TableListPage`), no esta card.
 *
 * La agenda se muestra **en hora local** (#22): lo que viaja es UTC y la conversión es de
 * `lib/date.ts`. Y si la mesa choca con algo a lo que el lector ya se comprometió, la card lo
 * advierte con el motivo a la vista (#178) - un aviso que no se explica es tan inútil como un botón
 * gris sin razón (principio 2 de frontend-diseno.md 1).
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
          {/* Si el nombre no entra se recorta él, nunca el karma: el karma es el dato que se compara. */}
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
