/**
 * The per-file cap, in bytes. Mirrors `app.storage.max-file-size` (2 MB).
 *
 * **A mirror and not the authority.** The server enforces it and answers `FILE_TOO_LARGE` with the
 * real number (#197); this exists so somebody is told at the moment they pick the file rather than
 * after filling in four wizard steps — which is the "a limit you only meet by breaking it" of
 * principio 2. If the two ever disagree the server wins, and the person sees its message.
 */
export const MAX_FILE_BYTES = 2 * 1024 * 1024

/**
 * What may be uploaded. Mirrors `app.storage.allowed-mime-types`, which took it from the legacy's
 * own whitelist (M21.4) — the one part of its file handling worth keeping.
 *
 * Same caveat as {@link MAX_FILE_BYTES}: the server is the authority, this is what makes the refusal
 * immediate instead of late.
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
 * @param file the file somebody just picked
 * @returns `FILE_TOO_LARGE`, `FILE_TYPE_NOT_ALLOWED`, `FILE_EMPTY`, or null when it is fine
 */
export function rejectionOf(file: File): 'FILE_TOO_LARGE' | 'FILE_TYPE_NOT_ALLOWED' | 'FILE_EMPTY' | null {
  if (file.size === 0) {
    return 'FILE_EMPTY'
  }
  if (file.size > MAX_FILE_BYTES) {
    return 'FILE_TOO_LARGE'
  }
  if (!(ALLOWED_MIME_TYPES as readonly string[]).includes(file.type)) {
    return 'FILE_TYPE_NOT_ALLOWED'
  }
  return null
}
