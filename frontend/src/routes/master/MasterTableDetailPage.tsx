import { useTranslation } from 'react-i18next'
import { NavLink, Outlet, useParams } from 'react-router'

import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { TableStatusBadge, useManagedTable } from '@/features/tables'
import { useMe } from '@/features/users'
import { ApiError } from '@/types/api'

const TAB_LINK_CLASSES = ({ isActive }: { isActive: boolean }) =>
  cn(
    '-mb-px border-b-2 px-1 pb-2 text-sm font-medium',
    isActive ? 'border-brand-fg text-fg' : 'border-transparent text-fg-muted hover:text-fg',
  )

/**
 * /master/tables/:id - the table as the people running it see it, with its tabs.
 *
 * The tabs are child routes rather than `useState` (#3.1.6 regla 5), so each has a URL, can be
 * linked, and the back button behaves.
 */
export function MasterTableDetailPage() {
  const { t } = useTranslation('master')
  const { id } = useParams<{ id: string }>()
  const tableId = id ?? ''
  // useManagedTable, no useGameTable: el backend verifica pertenencia antes de leer nada y
  // devuelve 403 sin cuerpo si el actor no es master de esta mesa - useGameTable es el detalle
  // público que cualquier jugador consulta en /tables/:id (decisiones.md #152).
  const { data: table, isPending, error, isLoadingError } = useManagedTable(tableId)
  const { data: me } = useMe()

  if (isPending) {
    return <Skeleton className="h-32 w-full" />
  }

  if (error instanceof ApiError && error.status === 403) {
    return <ForbiddenState />
  }

  if (isLoadingError || !table) {
    return <ErrorState />
  }

  const isPrimary = table.masters.some((master) => master.userId === me?.id && master.masterType === 'Primary')

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <h1 className="font-serif text-2xl font-semibold">{table.name}</h1>
        <TableStatusBadge status={table.status} />
      </div>
      <nav className="border-border-strong flex gap-4 border-b">
        <NavLink to="." end className={TAB_LINK_CLASSES}>
          {t('detail.tabs.candidates')}
        </NavLink>
        <NavLink to="status" className={TAB_LINK_CLASSES}>
          {t('detail.tabs.status')}
        </NavLink>
      </nav>
      <Outlet context={{ tableId, status: table.status, maxPlayers: table.maxPlayers, playerCount: table.playerCount, isPrimary }} />
    </div>
  )
}

export { MasterTableDetailPage as Component }
