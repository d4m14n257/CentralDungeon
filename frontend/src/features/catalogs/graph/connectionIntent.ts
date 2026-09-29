import type { AdminCatalogValue } from '../types'

/**
 * What connecting one value to another means on the catalog canvas (#275, #276):
 *
 * - `accept` - a proposal nobody classified yet joins the group (#55)
 * - `reassign` - an alias leaves its group for this one (#276)
 * - `merge` - a whole group, head and aliases, folds into this one (#55)
 */
export type CatalogConnectionIntent = 'accept' | 'reassign' | 'merge'

/**
 * Decides what dropping `source` on `target` does, or that it does nothing.
 *
 * **It is the whole grammar of the canvas**, and it is the same one the backend enforces, written
 * here so the canvas can refuse a connection while it is still being drawn instead of drawing it and
 * answering with a 409 (`isValidConnection`). The backend stays the authority; this is the grey
 * button principle 2 forbids, avoided.
 *
 * - The target has to head a group and be accepted: depth is always 1, so nothing hangs from an
 *   alias (#59), and nothing joins what is out of circulation.
 * - A value never connects to itself, nor to the group it is already in.
 * - A proposal (`Created` or `Rejected`, which #55 lets an admin reconsider) is accepted into it.
 * - An alias moves; a head carries its group and merges - only if it is accepted, like the server's
 *   merge asks.
 *
 * @param source the value the connection started from
 * @param target the value it was dropped on
 * @returns what the connection means, or null when the server would refuse it
 */
export function connectionIntent(source: AdminCatalogValue, target: AdminCatalogValue): CatalogConnectionIntent | null {
  if (source.id === target.id) return null
  if (target.canonicalId !== null || target.status !== 'Accepted') return null
  if (source.canonicalId === target.id) return null

  if (source.status === 'Created' || source.status === 'Rejected') return 'accept'
  if (source.canonicalId !== null) return 'reassign'
  return source.status === 'Accepted' ? 'merge' : null
}
