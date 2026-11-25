/**
 * The wire shape of a catalog value, in the root layer rather than inside `features/catalogs`.
 *
 * It lives here because **two features need it** and a feature never imports another: `catalogs` owns the six admin operations on these values, and `tables` receives them
 * nested inside a table's detail — a table arrives already carrying its systems, tags and
 * platforms, which is one round trip instead of four.
 *
 * The alternative was re-declaring the same three fields in `tables`, and that is exactly what
 * a second base type per entity forbids: the day the backend adds a field, one of the two
 * copies would keep describing the old shape.
 */

/**
 * A catalog value's lifecycle. A union of literals rather than a TS `enum`, because the backend
 * serializes it as a string and because `Record<CatalogStatus, …>` then forces every case to be
 * covered when mapping to a label or a badge variant (exhaustiveness).
 *
 * Only `Accepted` shows to players and filters; the other three exist for
 * /admin/catalogs.
 */
export type CatalogStatus = 'Created' | 'Accepted' | 'Rejected' | 'Disabled'

/**
 * What kind of thing a tag says about a table. A fixed set the backend owns and sends as a code;
 * the label is translated here, under `catalogs:tagCategory.<code>`. A tag has one or none.
 */
export type TagCategory = 'Theme' | 'PlayStyle' | 'ContentWarning' | 'SafetyTool' | 'Experience' | 'Language' | 'Age' | 'Format'

/**
 * Mirror of `CatalogValueResponse`: one system, tag or platform as everyone outside
 * /admin/catalogs sees it.
 *
 * No `canonicalId`. Which synonym group a value belongs to is an admin's concern; the equivalence
 * works in the search, not in the presentation.
 *
 * `status` travels because a master who has just proposed a value has to be told it is pending: a
 * table tagged with something the other players cannot see yet has to say so.
 */
export interface CatalogValue {
  id: string
  /** The value as its author wrote it — the alias, never rewritten to the group's canonical entry. */
  name: string
  status: CatalogStatus
  /**
   * The category of the value's group, which an alias reads from its canonical entry. Only tags have
   * one: always null for systems and platforms, and null for a tag nobody has classified.
   */
  category: TagCategory | null
  /**
   * What the value's group means, in plain text, read from the canonical entry for an alias. Only
   * tags have one; null otherwise. Rendered as text, never as markup.
   */
  description: string | null
}
