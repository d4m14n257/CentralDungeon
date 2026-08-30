import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'

import { ErrorState } from '@/components/ErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useDisclosure } from '@/hooks/useDisclosure'
import { ApplyToTableDialog, useMyApplications } from '@/features/registrations'
import { TableStatusBadge, useGameTable } from '@/features/tables'
import { useMe } from '@/features/users'
import type { GameTableDetail } from '@/features/tables'

function applyState(t: (key: string) => string, table: GameTableDetail, roles: string[], hasActiveApplication: boolean) {
  if (!roles.includes('Player')) {
    return { disabled: true, label: t('detail.needsPlayerRole') }
  }
  if (table.status !== 'Opened') {
    return { disabled: true, label: t('detail.notOpen') }
  }
  if (table.maxPlayers != null && table.playerCount >= table.maxPlayers) {
    return { disabled: true, label: t('detail.tableFull') }
  }
  if (hasActiveApplication) {
    return { disabled: true, label: t('detail.alreadyApplied') }
  }
  return { disabled: false, label: t('detail.apply') }
}

export function TableDetailPage() {
  const { t } = useTranslation('tables')
  const { id } = useParams<{ id: string }>()
  const tableId = id ?? ''
  const { data: table, isPending, isError } = useGameTable(tableId)
  const { data: me } = useMe()
  const { data: myApplications } = useMyApplications()
  const applyDialog = useDisclosure()

  if (isPending) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-1/2" />
        <Skeleton className="h-32 w-full" />
      </div>
    )
  }

  if (isError || !table) {
    return <ErrorState message={t('detail.notFoundDescription')} />
  }

  const hasActiveApplication =
    myApplications?.content.some(
      (registration) => registration.gameTableId === table.id && registration.status !== 'Rejected',
    ) ?? false

  const state = applyState(t, table, me?.roles ?? [], hasActiveApplication)

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <h1 className="font-serif text-2xl font-semibold">{table.name}</h1>
        <TableStatusBadge status={table.status} />
      </div>

      {table.description && (
        <section className="space-y-1">
          <h2 className="text-sm font-medium">{t('detail.description')}</h2>
          <p className="text-muted-foreground text-sm whitespace-pre-wrap">{table.description}</p>
        </section>
      )}

      {table.requirements && (
        <section className="space-y-1">
          <h2 className="text-sm font-medium">{t('detail.requirements')}</h2>
          <p className="text-muted-foreground text-sm whitespace-pre-wrap">{table.requirements}</p>
        </section>
      )}

      <section className="space-y-1">
        <h2 className="text-sm font-medium">{t('detail.masters')}</h2>
        <ul className="text-muted-foreground text-sm">
          {table.masters.map((master) => (
            <li key={master.userId}>
              {master.name} ({master.karma})
            </li>
          ))}
        </ul>
      </section>

      <Button disabled={state.disabled} onClick={() => applyDialog.open()}>
        {state.label}
      </Button>

      <ApplyToTableDialog tableId={table.id} tableName={table.name} open={applyDialog.isOpen} onOpenChange={applyDialog.close} />
    </div>
  )
}

export { TableDetailPage as Component }
