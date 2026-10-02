import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'

import { StatusNotice } from '@/components/StatusNotice'
import { adminQueuePath, adminTablesPath } from '@/config/paths'
import type { GameTableDetail } from '@/features/tables'

import { AdminTableActions, STATUSES_WITH_ADMIN_ACTIONS } from './AdminTableActions'
import { AdminTableDecisions, hasAdminDecisions } from './AdminTableDecisions'

/** What {@link AdminTableStatusNotice} takes. */
export interface AdminTableStatusNoticeProps {
  /** The table, as its admin view loaded it. */
  table: Pick<GameTableDetail, 'id' | 'name' | 'status'>
}

/**
 * Where the table stands, said above its tabs, with every decision an admin can take about it right
 * there (#287): review it, cancel it, assign it masters or remove it, pause it or resume it.
 *
 * **One notice and not a "Status" tab with a button in it.** The decisions first lived on that tab,
 * one section per kind - often a heading over a single button - and the user found it ugly and
 * hard to find: opening a table lands on Details. The notice says the situation in words («Esta
 * mesa espera tu revisión», «En curso») and carries its buttons in one row, visible from every tab.
 * The history, which is reading and not deciding, became a tab of its own.
 *
 * Every status has its sentence, including the ones with nothing to decide - a draft not sent yet,
 * a finished table - so the notice never disappears depending on the case and the reader learns
 * where to look. Every decision tells the table's masters (#244, #284).
 *
 * @param props.table the table
 */
export function AdminTableStatusNotice({ table }: AdminTableStatusNoticeProps) {
  const { t } = useTranslation('admin')
  const navigate = useNavigate()
  const hasActions = STATUSES_WITH_ADMIN_ACTIONS.includes(table.status) || hasAdminDecisions(table.status)

  const description =
    table.status === 'PauseRequested' ? (
      <>
        {t('tables.notice.PauseRequested.description')}{' '}
        <Link to={adminQueuePath()} className="hover:text-fg underline">
          {t('tables.detail.goToQueue')}
        </Link>
      </>
    ) : (
      t(`tables.notice.${table.status}.description`)
    )

  return (
    <StatusNotice
      title={t(`tables.notice.${table.status}.title`)}
      description={description}
      actions={
        hasActions && (
          <>
            <AdminTableDecisions table={table} />
            {/* Removed, the table no longer has a detail to show: back to the list it was found in. */}
            <AdminTableActions table={table} presentation="notice" onDeleted={() => void navigate(adminTablesPath())} />
          </>
        )
      }
    />
  )
}
