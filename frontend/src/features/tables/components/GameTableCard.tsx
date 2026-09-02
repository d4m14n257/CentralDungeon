import { Check } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { Card } from '@/components/ui/card'
import { tableDetailPath } from '@/config/paths'

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
