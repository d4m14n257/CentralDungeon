import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { tableDetailPath } from '@/config/paths'

import type { GameTableSummary } from '../types'
import { TableStatusBadge } from './TableStatusBadge'

export function GameTableCard({ table, linkTo }: { table: GameTableSummary; linkTo?: string }) {
  const { t } = useTranslation('tables')

  return (
    <Link to={linkTo ?? tableDetailPath(table.id)}>
      <Card className="hover:border-primary/50 h-full transition-colors">
        <CardHeader className="flex flex-row items-start justify-between gap-2">
          <CardTitle className="text-base">{table.name}</CardTitle>
          <TableStatusBadge status={table.status} />
        </CardHeader>
        <CardContent className="text-muted-foreground space-y-1 text-sm">
          {table.tableTypeName && <p>{table.tableTypeName}</p>}
          <p>
            {table.maxPlayers != null
              ? t('explorer.players', { current: table.playerCount, max: table.maxPlayers })
              : t('explorer.playersUnlimited', { current: table.playerCount })}
          </p>
          <p>{t('explorer.master', { name: table.primaryMaster.name })}</p>
        </CardContent>
      </Card>
    </Link>
  )
}
