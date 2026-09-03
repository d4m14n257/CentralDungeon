import type { CatalogValue } from '@/types/catalog'

export type { CatalogStatus, CatalogValue } from '@/types/catalog'

/**
 * Which of the three catalogs a call is about. It is the path segment the API expects, so the value
 * that travels and the value the UI branches on are the same string - there is no lookup table
 * between them to get out of step.
 */
export type CatalogKind = 'systems' | 'tags' | 'platforms'

/**
 * Mirror of `AdminCatalogValueResponse` - **the one type of this feature written by hand** (#3.2).
 *
 * It extends the public shape rather than restating it: what /admin/catalogs receives is what
 * everyone else receives plus the four fields only an admin has business seeing. `CatalogValue`
 * itself lives in `types/catalog.ts` because `features/tables` needs it too and a feature never
 * imports another (regla dura 16).
 */
export interface AdminCatalogValue extends CatalogValue {
  /** The group this value belongs to, or null when it *is* its group's canonical entry (#59). */
  canonicalId: string | null
  /** The canonical entry's name, so a row can read "DANDD → D&D 5e" without a second request. */
  canonicalName: string | null
  /** How many tables link to this value - not to its group. What a disable decision rests on (#81). */
  uses: number
  /** ISO-8601 UTC. The conversion to the reader's zone is the frontend's (#22, #111). */
  createdAt: string
}

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
