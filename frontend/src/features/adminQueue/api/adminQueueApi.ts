import { api } from '@/api/client'
import { pageSize } from '@/config/pagination'

import type { AdminQueueItem, AdminQueueItemType } from '../types'

/**
 * The three calls of the shared admin tray (#100, F3.3).
 *
 * **The actor is never a parameter.** Who is reading the tray, who reserves and who releases all come
 * from the token (arquitectura.md §2.6); an id in the path would be a claim the caller makes about
 * themselves, and here it would be a claim about *somebody else's* reservation.
 *
 * **There is no `q`, and that is a decision and not an omission** (§2 del contrato). A tray sorts
 * itself by who has been waiting longest and its job is to empty; a search box over it would answer a
 * question nobody standing in front of it is asking. `/admin/tables` is the screen that searches
 * (#176).
 *
 * **An item addressed by `(type, id)` and not by an id of its own**: there is no `admin_queue` table
 * and there will not be one (#11) — the tray is a merge over the tables that already hold the work.
 */
export const adminQueueApi = {
  /**
   * One page of the tray: everything waiting on an admin that is free or already theirs.
   *
   * Oldest first (#136) — urgency here is time, not volume — with the id as the tie-break so page two
   * is stable (#171).
   *
   * @param page zero-based page number
   */
  list: (page = 0) => api.getPage<AdminQueueItem>('/api/v1/admin-queue', { page, size: pageSize.adminQueue }),

  /**
   * Takes an item for the reader, which is what resolving it afterwards requires (#100).
   *
   * **Idempotent for whoever already holds it** — `200`, and the reservation does not restart — and
   * refused with `409 ITEM_ALREADY_CLAIMED` when a colleague got there first. That refusal is the
   * mechanism working: it is the one thing polling cannot rule out.
   *
   * @param type which source the item came from
   * @param id   its id in that source's own table
   */
  claim: (type: AdminQueueItemType, id: string) => api.post<AdminQueueItem>(`/api/v1/admin-queue/${type}/${id}/claim`),

  /**
   * Hands an item back to the tray.
   *
   * **Releasing something nobody holds is a `204` too**, because the desired state already holds;
   * releasing a colleague's is `409 ITEM_ALREADY_CLAIMED`, because that one is not the reader's to
   * hand back.
   *
   * @param type which source the item came from
   * @param id   its id in that source's own table
   */
  release: (type: AdminQueueItemType, id: string) => api.delete(`/api/v1/admin-queue/${type}/${id}/claim`),
}
