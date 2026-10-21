import type { AdminFile } from './types'
import { PUBLISHABLE_CATEGORIES } from './categories'

/**
 * Where a file stands in the platform's library (ADR-14 §7).
 *
 * - `Unpublished`: uploaded and never published — no master sees it yet.
 * - `Published`: offered in the pickers; tables can attach it (ADR-14 §1).
 * - `Hidden`: published once, offered to nobody now. Tables that attached it keep it.
 * - `Removed`: marked gone (ADR-03 §7).
 *
 * A union of literals (arquitectura §3.2), so every `Record` over it has to decide all four.
 */
export type LibraryState = 'Unpublished' | 'Published' | 'Hidden' | 'Removed'

/**
 * Derives the library state of a file from what the server sends, rather than the server sending a
 * fourth field saying the same thing twice (ADR-03 §1, ADR-14 §3): `Public` is published, `Library` with a first
 * publication is hidden, `Library` without one has never been published.
 *
 * @param file the row of /admin/files
 * @returns where it stands
 */
export function libraryStateOf(file: Pick<AdminFile, 'fileType' | 'publishedAt' | 'status'>): LibraryState {
  if (file.status === 'Deleted') return 'Removed'
  if (file.fileType === 'Public') return 'Published'
  return file.publishedAt !== null ? 'Hidden' : 'Unpublished'
}

/**
 * Whether a library file says where it would be offered, which is what publishing needs (ADR-14 §7).
 *
 * The same rule the server enforces with `FILE_NEEDS_CATEGORY`: at least one of the cajones a file can
 * be published into. The upload always asks for one, so a file failing this is one whose cajón was lost
 * along the way - an upload from before ADR-14 §7, for instance. Knowing it on this side is what lets the
 * row say "choose what it is first" instead of offering a button the server would turn down.
 *
 * @param file the row of /admin/files
 * @returns true when it can be published as it stands
 */
export function hasPublishableCategory(file: Pick<AdminFile, 'categories'>): boolean {
  return file.categories.some((category) => (PUBLISHABLE_CATEGORIES as readonly string[]).includes(category))
}
