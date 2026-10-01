import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useOutletContext } from 'react-router'

import { adminQueuePath, adminTablesPath } from '@/config/paths'
import type { GameTableDetail } from '@/features/tables'

import { StatusTimeline } from '../master/MasterTableStatusTab'
import { AdminTableActions, STATUSES_WITH_ADMIN_ACTIONS } from './AdminTableActions'

interface OutletContext {
  table: GameTableDetail
}

/**
 * The status tab of `/admin/tables/:id` (#284): the admin's own actions on the table, and its whole
 * history with the reason behind each step.
 *
 * **The admin's actions, not the master's transitions.** Sending to review, starting and finishing
 * are the master's; what an admin does to a table is assign it masters, remove one nobody runs, and
 * pause or resume it - the same `AdminTableActions` a row of `/admin/tables` offers, so the two cannot
 * disagree about which action fits which status. Each of them tells the table's masters (#284).
 *
 * **Reviewing is not here**, and the tab says where it is: approving and requesting changes live in
 * the tray, where a review can be reserved so two admins never answer the same one (#100, #176).
 */
export function AdminTableStatusTab() {
  const { t } = useTranslation('admin')
  const { t: tMaster } = useTranslation('master')
  const { table } = useOutletContext<OutletContext>()
  const navigate = useNavigate()
  const hasActions = STATUSES_WITH_ADMIN_ACTIONS.includes(table.status)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="section-label">{t('tables.detail.actionsTitle')}</h2>
        {hasActions && (
          <div className="row-actions">
            {/* Removed, the table no longer has a detail to show: back to the list it was found in. */}
            <AdminTableActions table={table} onDeleted={() => void navigate(adminTablesPath())} />
          </div>
        )}
      </div>
      {/* Said rather than left as an empty row (principio 2): editing is still in the header. */}
      {!hasActions && <p className="text-fg-muted text-sm">{t('tables.detail.noActions')}</p>}
      {table.status === 'Preparation' && (
        <p className="text-fg-muted text-sm">
          {t('tables.detail.reviewInQueue')}{' '}
          <Link to={adminQueuePath()} className="hover:text-fg underline">
            {t('tables.detail.goToQueue')}
          </Link>
        </p>
      )}
      <div className="space-y-2">
        <h2 className="text-sm font-medium">{tMaster('status.historyTitle')}</h2>
        <StatusTimeline tableId={table.id} />
      </div>
    </div>
  )
}

export { AdminTableStatusTab as Component }
