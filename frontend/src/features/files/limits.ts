/**
 * What may be uploaded. Mirrors `app.storage.allowed-mime-types`, which took it from the legacy's
 * own whitelist (M21.4) — the one part of its file handling worth keeping.
 *
 * **A mirror and not the authority**: the server enforces it and answers `FILE_TYPE_NOT_ALLOWED`;
 * this is what makes the refusal immediate instead of arriving after four wizard steps, which is the
 * "a limit you only meet by breaking it" of principio 2.
 *
 * Unlike the size cap, this one is still deployment configuration and **not** a setting (#141): which
 * MIME types the parser accepts is a decision of whoever deploys the application. The cap left this
 * file in F3.5 — see `hooks/useClientLimits`.
 */
export const ALLOWED_MIME_TYPES = [
  'image/png',
  'image/jpeg',
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
] as const

/**
 * Why a file cannot be staged, or null when it can.
 *
 * Returns the **error code the backend would have used**, so the screen renders the same sentence
 * either way and there is exactly one wording per problem (#197). A file refused here and a file
 * refused there read identically, because they are the same refusal arriving sooner.
 *
 * **The cap arrives as a parameter since F3.5**, because it stopped being a constant: an admin
 * changes it from `/admin/settings` without a deploy (#141), so a hardcoded mirror here would refuse
 * files the server would take. The caller reads it from `useClientLimits`.
 *
 * @param file     the file somebody just picked
 * @param maxBytes the per-file cap in force, from `useClientLimits`
 * @returns `FILE_TOO_LARGE`, `FILE_TYPE_NOT_ALLOWED`, `FILE_EMPTY`, or null when it is fine
 */
export function rejectionOf(file: File, maxBytes: number): 'FILE_TOO_LARGE' | 'FILE_TYPE_NOT_ALLOWED' | 'FILE_EMPTY' | null {
  if (file.size === 0) {
    return 'FILE_EMPTY'
  }
  if (file.size > maxBytes) {
    return 'FILE_TOO_LARGE'
  }
  if (!(ALLOWED_MIME_TYPES as readonly string[]).includes(file.type)) {
    return 'FILE_TYPE_NOT_ALLOWED'
  }
  return null
}
