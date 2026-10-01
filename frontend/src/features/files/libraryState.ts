import type { AdminFile } from './types'

/**
 * Where a file stands in the platform's library (#282).
 *
 * - `Unpublished`: uploaded and never published — no master sees it yet.
 * - `Published`: offered in the pickers; tables can attach it (#79).
 * - `Hidden`: published once, offered to nobody now. Tables that attached it keep it.
 * - `Removed`: marked gone (#25).
 *
 * A union of literals (arquitectura §3.2), so every `Record` over it has to decide all four.
 */
export type LibraryState = 'Unpublished' | 'Published' | 'Hidden' | 'Removed'

/**
 * Derives the library state of a file from what the server sends, rather than the server sending a
 * fourth field saying the same thing twice (#11, #232): `Public` is published, `Library` with a first
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
