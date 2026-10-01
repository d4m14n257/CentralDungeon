import { useTranslation } from 'react-i18next'
import { Outlet, useParams } from 'react-router'

import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { PageHeader } from '@/components/PageHeader'
import { TabNav } from '@/components/TabNav'
import { Skeleton } from '@/components/ui/skeleton'
import { adminTableEditPath, adminTablesPath } from '@/config/paths'
import { ADMIN_EDITABLE_STATUSES, TableStatusBadge, useManagedTable } from '@/features/tables'
import { ApiError } from '@/types/api'

/**
 * `/admin/tables/:id` — one table, whole, as an admin reads it (#284).
 *
 * **The admin has no visibility limits** (#45) — the authorship of a comment is the only thing
 * withheld — and until #284 the only part of a table an admin could open was its status history.
 * This screen is the rest: the same tabs its master works in, fed by the same management reads, which
 * the backend now answers for an admin too (`MasterService.canOversee`).
 *
 * **The tabs are the master's own modules, mounted read-only** rather than a second set written for
 * the admin. The context below carries `readOnly`, and each tab hides what is the master's to press -
 * accepting a candidate, recording attendance, publishing a request, attaching a file. What the admin
 * *does* to a table is its own: rewriting it (the header's action) and the actions of the status tab.
 * Every one of them tells the table's masters (#284).
 *
 * Two tabs are the admin's and not the master's: **Details** goes first, because the master wrote
 * the table and does not need it read back to them, and **Status** offers the admin's actions instead
 * of the master's transitions.
 */
export function AdminTableDetailPage() {
  const { t } = useTranslation('admin')
  const { t: tMaster } = useTranslation('master')
  const { id } = useParams<{ id: string }>()
  const tableId = id ?? ''
  // The management read, not the public one: it carries what only the people running a table see,
  // and the backend checks the admin rank before mapping anything (#152, #284).
  const { data: table, isPending, error, isLoadingError, refetch } = useManagedTable(tableId)

  if (isPending) {
    return <Skeleton className="h-32 w-full" />
  }

  if (error instanceof ApiError && error.status === 403) {
    return <ForbiddenState />
  }

  if (isLoadingError || !table) {
    return <ErrorState onRetry={() => void refetch()} />
  }

  // Offered only where the backend would take it: a closed table is a record and nobody rewrites it.
  const canEdit = ADMIN_EDITABLE_STATUSES.includes(table.status)
  const primary = table.masters.find((master) => master.masterType === 'Primary')

  return (
    <div className="space-y-6">
      <PageHeader
        title={table.name}
        badge={<TableStatusBadge status={table.status} />}
        back={{ to: adminTablesPath(), label: t('tables.detail.back') }}
        help="admins.table-detail"
        description={primary ? t('tables.detail.ranBy', { name: primary.name }) : t('tables.noPrimaryMaster')}
        action={canEdit ? { label: t('tables.detail.edit'), to: adminTableEditPath(tableId) } : undefined}
      />
      <TabNav
        label={tMaster('detail.tabs.label')}
        items={[
          { to: '.', label: t('tables.detail.tabs.details'), end: true },
          { to: 'candidates', label: tMaster('detail.tabs.candidates') },
          { to: 'players', label: tMaster('detail.tabs.players') },
          { to: 'schedule', label: tMaster('detail.tabs.schedule') },
          { to: 'sessions', label: tMaster('detail.tabs.sessions') },
          { to: 'tasks', label: tMaster('detail.tabs.tasks') },
          { to: 'files', label: tMaster('detail.tabs.files') },
          { to: 'status', label: tMaster('detail.tabs.status') },
        ]}
      />
      <Outlet
        context={{
          tableId,
          table,
          status: table.status,
          maxPlayers: table.maxPlayers,
          playerCount: table.playerCount,
          // Never the Primary here, even when the admin also runs this table: on this screen they act
          // as an admin, and the master's buttons are on /master/tables/:id.
          isPrimary: false,
          masters: table.masters,
          schedule: table.schedule,
          startDate: table.startDate,
          totalSessions: table.totalSessions,
          readOnly: true,
        }}
      />
    </div>
  )
}

export { AdminTableDetailPage as Component }
