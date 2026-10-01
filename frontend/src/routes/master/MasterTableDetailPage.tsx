import { useTranslation } from 'react-i18next'
import { Outlet, useParams } from 'react-router'

import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { Skeleton } from '@/components/ui/skeleton'
import { PageHeader } from '@/components/PageHeader'
import { TabNav } from '@/components/TabNav'
import { masterTableEditPath } from '@/config/paths'
import { MASTER_EDITABLE_STATUSES, TableStatusBadge, useManagedTable } from '@/features/tables'
import { useMe } from '@/features/users'
import { ApiError } from '@/types/api'

/** The two states where the backend still accepts a rewrite of the table (#189). */

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
  // detail any player reads on /player/tables/:id (decisiones.md #152).
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
  const canEdit = isPrimary && MASTER_EDITABLE_STATUSES.includes(table.status)

  return (
    <div className="space-y-6">
      <PageHeader
        title={table.name}
        badge={<TableStatusBadge status={table.status} />}
        action={canEdit ? { label: t('detail.edit'), to: masterTableEditPath(tableId) } : undefined}
      />
      <TabNav
        label={t('detail.tabs.label')}
        items={[
          { to: '.', label: t('detail.tabs.candidates'), end: true },
          { to: 'players', label: t('detail.tabs.players') },
          { to: 'schedule', label: t('detail.tabs.schedule') },
          { to: 'sessions', label: t('detail.tabs.sessions') },
          { to: 'tasks', label: t('detail.tabs.tasks') },
          { to: 'files', label: t('detail.tabs.files') },
          { to: 'status', label: t('detail.tabs.status') },
        ]}
      />
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
          totalSessions: table.totalSessions,
        }}
      />
    </div>
  )
}

export { MasterTableDetailPage as Component }
