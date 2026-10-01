import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useOutletContext } from 'react-router'

import { adminQueuePath, adminTablesPath } from '@/config/paths'
import type { GameTableDetail } from '@/features/tables'

import { StatusTimeline } from '../master/MasterTableStatusTab'
import { AdminTableActions, STATUSES_WITH_ADMIN_ACTIONS } from './AdminTableActions'
import { AdminTableDecisions, hasAdminDecisions } from './AdminTableDecisions'

interface OutletContext {
  table: GameTableDetail
}

/**
 * The status tab of `/admin/tables/:id` (#284, #286): what an admin decides about the table, and its
 * whole history with the reason behind each step.
 *
 * **The admin's decisions, not the master's transitions.** Sending to review, starting and finishing
 * are the master's. What an admin does is in two groups: the **decisions** that change where the
 * table stands - approve it or send it back while it is in review, cancel it (`AdminTableDecisions`,
 * #286) - and the **actions** a row of `/admin/tables` offers too: assign masters, remove one nobody
 * runs, pause or resume (`AdminTableActions`). Every one tells the table's masters (#244, #284).
 *
 * **Reviewing is here as well as in the tray** (#286): an admin who has read the whole table decides
 * from it. Same endpoints and same reservation rule (#100), so a table a colleague took from the tray
 * is refused here too. A master's *request* to pause is an approval request and still resolves in the
 * tray, which the tab says.
 */
export function AdminTableStatusTab() {
  const { t } = useTranslation('admin')
  const { t: tMaster } = useTranslation('master')
  const { table } = useOutletContext<OutletContext>()
  const navigate = useNavigate()
  const hasActions = STATUSES_WITH_ADMIN_ACTIONS.includes(table.status)
  const hasDecisions = hasAdminDecisions(table.status)

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
      {table.status === 'Preparation' && <p className="text-fg-muted text-sm">{t('tables.detail.reviewHere')}</p>}
      {table.status === 'PauseRequested' && (
        <p className="text-fg-muted text-sm">
          {t('tables.detail.pauseRequestInQueue')}{' '}
          <Link to={adminQueuePath()} className="hover:text-fg underline">
            {t('tables.detail.goToQueue')}
          </Link>
        </p>
      )}
      {hasDecisions && <AdminTableDecisions table={table} />}
      {/* Said rather than left as an empty block (principio 2): editing is still in the header. */}
      {!hasActions && !hasDecisions && <p className="text-fg-muted text-sm">{t('tables.detail.noActions')}</p>}
      <div className="space-y-2 pt-2">
        <h2 className="text-sm font-medium">{tMaster('status.historyTitle')}</h2>
        <StatusTimeline tableId={table.id} />
      </div>
    </div>
  )
}

export { AdminTableStatusTab as Component }
