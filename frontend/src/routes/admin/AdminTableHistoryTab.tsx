import { useOutletContext } from 'react-router'

import type { GameTableDetail } from '@/features/tables'

import { StatusTimeline } from '../master/MasterTableStatusTab'

interface OutletContext {
  table: GameTableDetail
}

/**
 * The History tab of `/admin/tables/:id` (#287): every step of the table's life, who took it and why.
 *
 * **Reading, not deciding.** It was the "Status" tab and carried the admin's actions too, often as a
 * heading over a single button; the decisions moved to the status notice above the tabs
 * (`AdminTableStatusNotice`), where they are seen from every tab, and what was left is the history.
 */
export function AdminTableHistoryTab() {
  const { table } = useOutletContext<OutletContext>()
  return <StatusTimeline tableId={table.id} />
}

export { AdminTableHistoryTab as Component }
