import type { FileCategory } from './types'

/**
 * Every cajón, in the order a table lives through them (#233).
 *
 * A written-out tuple rather than something derived from the type, because a union of literals has no
 * runtime value to iterate. Typed as `readonly FileCategory[]` so dropping a value the union still
 * has is a compile error in whichever `Record<FileCategory, …>` needs it, and adding one the union
 * does not have is a compile error here.
 */
export const FILE_CATEGORIES = [
  'TableMaterial',
  'MasterRequest',
  'PlayerApplication',
  'PlayerSubmission',
  'Announcement',
] as const satisfies readonly FileCategory[]

/**
 * The cajones an admin may publish into (#233).
 *
 * The two player-side ones are absent, and not because of a permission check bolted on afterwards:
 * they hold what individual people answered with, and a blank offered to the whole community is not
 * an answer — it belongs in the master-side cajón the request was written from. The backend refuses
 * the others too; this is what keeps the screen from offering a choice that would be rejected.
 */
export const PUBLISHABLE_CATEGORIES = ['TableMaterial', 'MasterRequest', 'Announcement'] as const satisfies readonly FileCategory[]
