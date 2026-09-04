import { useTranslation } from 'react-i18next'
import { Link, NavLink, Outlet, useParams } from 'react-router'

import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { masterTableEditPath } from '@/config/paths'
import { cn } from '@/lib/utils'
import { TableStatusBadge, useManagedTable } from '@/features/tables'
import type { GameTableStatus } from '@/features/tables'
import { useMe } from '@/features/users'
import { ApiError } from '@/types/api'

const TAB_LINK_CLASSES = ({ isActive }: { isActive: boolean }) =>
  cn(
    '-mb-px border-b-2 px-1 pb-2 text-sm font-medium',
    isActive ? 'border-brand-fg text-fg' : 'border-transparent text-fg-muted hover:text-fg',
  )

/** The two states where the backend still accepts a rewrite of the table (#189). */
const EDITABLE_STATUSES: GameTableStatus[] = ['Preparation', 'ChangesRequested']

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
  // useManagedTable and not useGameTable: the backend checks membership before reading anything and
  // answers 403 with no body when the actor does not run this table - useGameTable is the public
  // detail any player reads on /tables/:id (decisiones.md #152).
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
  // The edit form is offered only where the backend would accept it. A button that appears when it
  // cannot work is worse than no button (principio 2 de frontend-diseno.md 1).
  const canEdit = isPrimary && EDITABLE_STATUSES.includes(table.status)

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <h1 className="font-serif text-2xl font-semibold">{table.name}</h1>
        <div className="flex shrink-0 items-center gap-3">
          {canEdit && (
            <Button asChild size="sm" variant="secondary">
              <Link to={masterTableEditPath(tableId)}>{t('detail.edit')}</Link>
            </Button>
          )}
          <TableStatusBadge status={table.status} />
        </div>
      </div>
      <nav className="border-border-strong flex flex-wrap gap-4 border-b">
        <NavLink to="." end className={TAB_LINK_CLASSES}>
          {t('detail.tabs.candidates')}
        </NavLink>
        <NavLink to="players" className={TAB_LINK_CLASSES}>
          {t('detail.tabs.players')}
        </NavLink>
        <NavLink to="schedule" className={TAB_LINK_CLASSES}>
          {t('detail.tabs.schedule')}
        </NavLink>
        <NavLink to="sessions" className={TAB_LINK_CLASSES}>
          {t('detail.tabs.sessions')}
        </NavLink>
        <NavLink to="tasks" className={TAB_LINK_CLASSES}>
          {t('detail.tabs.tasks')}
        </NavLink>
        <NavLink to="files" className={TAB_LINK_CLASSES}>
          {t('detail.tabs.files')}
        </NavLink>
        <NavLink to="status" className={TAB_LINK_CLASSES}>
          {t('detail.tabs.status')}
        </NavLink>
      </nav>
      <Outlet
        context={{
          tableId,
          status: table.status,
          maxPlayers: table.maxPlayers,
          playerCount: table.playerCount,
          isPrimary,
          masters: table.masters,
          schedule: table.schedule,
          startDate: table.startDate,
          duration: table.duration,
          totalSessions: table.totalSessions,
        }}
      />
    </div>
  )
}

export { MasterTableDetailPage as Component }
