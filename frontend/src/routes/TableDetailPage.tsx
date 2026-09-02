import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'

import { ErrorState } from '@/components/ErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useDisclosure } from '@/hooks/useDisclosure'
import { ApplyToTableDialog, useMyApplications } from '@/features/registrations'
import { TableStatusBadge, useGameTable } from '@/features/tables'
import { useMe } from '@/features/users'
import type { GameTableDetail, MasterSummary } from '@/features/tables'

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

/** El principal: cualquier co-master queda relegado a la línea de masters (frontend-diseno.md 4). */
function primaryMasterOf(masters: MasterSummary[]) {
  return masters.find((master) => master.masterType === 'Primary') ?? masters[0]
}

export function TableDetailPage() {
  const { t, i18n } = useTranslation('tables')
  const { id } = useParams<{ id: string }>()
  const tableId = id ?? ''
  // isLoadingError, no isError: si ya se cargó la mesa, un refetch de fondo que falla no debe
  // reemplazar la pantalla por un error - dejaría de verse algo que hasta hace un segundo andaba
  // bien (docs/decisiones.md #150).
  const { data: table, isPending, isLoadingError } = useGameTable(tableId)
  const { data: me } = useMe()
  const { data: myApplications } = useMyApplications()
  const applyDialog = useDisclosure()

  if (isPending) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-1/2" />
        <Skeleton className="h-48 w-full" />
      </div>
    )
  }

  if (isLoadingError || !table) {
    return <ErrorState message={t('detail.notFoundDescription')} />
  }

  const hasActiveApplication =
    myApplications?.content.some((registration) => registration.gameTableId === table.id && registration.status !== 'Rejected') ?? false

  const state = applyState(t, table, me?.roles ?? [], hasActiveApplication)
  const primaryMaster = primaryMasterOf(table.masters)
  const coMasters = table.masters.filter((master) => master.userId !== primaryMaster?.userId)

  return (
    <div className="border-border-strong bg-surface rounded-xl border p-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="font-serif text-2xl font-semibold">{table.name}</h1>
          {primaryMaster && (
            <p className="text-fg-muted mt-1 flex flex-wrap items-center gap-x-1 text-sm">
              <span>{t('detail.masterLabel')}:</span>
              <span>{primaryMaster.name}</span>
              <span>·</span>
              <span className="text-brand-fg">{primaryMaster.karma.toLocaleString(i18n.language)}</span>
              {coMasters.length > 0 && (
                <>
                  <span>·</span>
                  <span>{t('detail.coMasterLabel')}:</span>
                  <span>{coMasters.map((master) => master.name).join(', ')}</span>
                </>
              )}
            </p>
          )}
        </div>
        <TableStatusBadge status={table.status} />
      </div>

      <div className="border-border mt-4 flex flex-col gap-4 border-t pt-4">
        {table.description && <p className="text-fg-muted max-w-prose text-sm leading-6 whitespace-pre-wrap">{table.description}</p>}

        {table.requirements && (
          <section>
            <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('detail.requirements')}</h2>
            <p className="mt-1.5 text-sm leading-6 whitespace-pre-wrap">{table.requirements}</p>
          </section>
        )}

        <div>
          <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('detail.capacity')}</h2>
          <p className="mt-1.5 text-sm">
            {table.maxPlayers != null
              ? t('explorer.players', { current: table.playerCount, max: table.maxPlayers })
              : t('explorer.playersUnlimited', { current: table.playerCount })}
          </p>
        </div>
      </div>

      <div className="border-border mt-4 flex justify-end border-t pt-4">
        <Button disabled={state.disabled} onClick={() => applyDialog.open()}>
          {state.label}
        </Button>
      </div>

      <ApplyToTableDialog tableId={table.id} tableName={table.name} open={applyDialog.isOpen} onOpenChange={applyDialog.close} />
    </div>
  )
}

export { TableDetailPage as Component }
