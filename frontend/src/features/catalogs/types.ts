/**
 * Which of the three catalogs a call is about. It is the path segment the API expects, so the value
 * that travels and the value the UI branches on are the same string - there is no lookup table
 * between them to get out of step.
 */
export type CatalogKind = 'systems' | 'tags' | 'platforms'

/**
 * A catalog value's lifecycle. A union of literals rather than a TS `enum`, because the backend
 * serializes it as a string and because `Record<CatalogStatus, …>` then forces every case to be
 * covered when mapping to a label or a badge variant (#3.2 regla 9).
 *
 * Only `Accepted` shows to players and filters (#57, #81); the other three exist for
 * /admin/catalogs.
 */
export type CatalogStatus = 'Created' | 'Accepted' | 'Rejected' | 'Disabled'

/**
 * Mirror of `AdminCatalogValueResponse` - **the one type of this feature written by hand** (#3.2).
 *
 * The admin shape is the base rather than the public one because it is the wider of the two: what a
 * player receives is a strict subset of it, so `CatalogValue` derives from here and the two cannot
 * drift apart the day the backend adds a field.
 */
export interface AdminCatalogValue {
  id: string
  name: string
  status: CatalogStatus
  /** The group this value belongs to, or null when it *is* its group's canonical entry (#59). */
  canonicalId: string | null
  /** The canonical entry's name, so a row can read "DANDD → D&D 5e" without a second request. */
  canonicalName: string | null
  /** How many tables link to this value - not to its group. What a disable decision rests on (#81). */
  uses: number
  /** ISO-8601 UTC. The conversion to the reader's zone is the frontend's (#22, #111). */
  createdAt: string
}

/**
 * Mirror of `CatalogValueResponse` - what the combobox and, later, the explorer's filters receive.
 *
 * Derived, not re-declared: it is the admin value minus everything only an admin has business
 * seeing. `status` stays because a master who just proposed a value has to be told it is pending
 * (#57).
 */
export type CatalogValue = Pick<AdminCatalogValue, 'id' | 'name' | 'status'>

/** What proposing sends. The status is the server's to decide - it is always born `Created` (#55). */
export type ProposeCatalogValueInput = Pick<AdminCatalogValue, 'name'>

/**
 * What accepting sends: the group to join, or null to accept the value as a canonical entry of its
 * own (#55). The target has to be canonical itself - depth is always 1 (#59).
 */
export type AcceptCatalogValueInput = Pick<AdminCatalogValue, 'canonicalId'>

/**
 * What merging sends. Both sides have to be canonical entries: the source stops being a group and
 * everything it held - its aliases and itself - starts pointing at the target (#55, #59).
 */
export interface MergeCatalogGroupsInput {
  /** The group that stops being one. */
  sourceCanonicalId: string
  /** The group that survives, and keeps its name. */
  targetCanonicalId: string
}

/** What splitting sends: the alias that leaves its group and becomes canonical on its own (#55). */
export interface SplitCatalogGroupInput {
  memberId: string
}

/**
 * What disabling sends.
 *
 * `newCanonicalId` is only needed when the value being disabled is a canonical entry that still has
 * live aliases: under a flat `canonical_id`, disabling the canonical and changing the canonical are
 * the same operation (#59), and the successor is the admin's choice rather than an arbitrary first
 * alias (#55).
 */
export interface DisableCatalogValueInput {
  newCanonicalId: string | null
}
