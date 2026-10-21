import { TableEditPage } from '../master/MasterTableEditPage'

/**
 * `/admin/tables/:id/edit` — an admin rewriting somebody else's table (ADR-06 §8).
 *
 * The form is the master's, mounted in its admin mode rather than copied: the fields and the
 * replace-not-patch rule (ADR-04 §7) are the same, and what differs - the endpoint, when it is allowed,
 * what the review warns about - is decided by `asAdmin`. Saving tells every master of the table.
 */
export function AdminTableEditPage() {
  return <TableEditPage asAdmin />
}

export { AdminTableEditPage as Component }
