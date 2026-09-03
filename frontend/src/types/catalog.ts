/**
 * The wire shape of a catalog value, in the root layer rather than inside `features/catalogs`.
 *
 * It lives here because **two features need it** and a feature never imports another (§3.1.2, regla
 * dura 16): `catalogs` owns the six admin operations on these values, and `tables` receives them
 * nested inside a table's detail — a table arrives already carrying its systems, tags and
 * platforms, which is one round trip instead of four.
 *
 * The alternative was re-declaring the same three fields in `tables`, and that is exactly what
 * "un tipo base por entidad" (§3.2) forbids: the day the backend adds a field, one of the two
 * copies would keep describing the old shape.
 */

/**
 * A catalog value's lifecycle. A union of literals rather than a TS `enum`, because the backend
 * serializes it as a string and because `Record<CatalogStatus, …>` then forces every case to be
 * covered when mapping to a label or a badge variant (§3.2 regla 9).
 *
 * Only `Accepted` shows to players and filters (#57, #81); the other three exist for
 * /admin/catalogs.
 */
export type CatalogStatus = 'Created' | 'Accepted' | 'Rejected' | 'Disabled'

/**
 * Mirror of `CatalogValueResponse`: one system, tag or platform as everyone outside
 * /admin/catalogs sees it.
 *
 * No `canonicalId`. Which synonym group a value belongs to is an admin's concern; the equivalence
 * works in the search, not in the presentation (#58).
 *
 * `status` travels because a master who has just proposed a value has to be told it is pending: a
 * table tagged with something the other players cannot see yet has to say so (#57).
 */
export interface CatalogValue {
  id: string
  /** The value as its author wrote it — the alias, never rewritten to the group's canonical entry (#58). */
  name: string
  status: CatalogStatus
}
