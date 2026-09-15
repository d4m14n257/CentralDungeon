import { ApiError } from '@/types/api'

/**
 * The six refusals F3.1 added, each with a sentence of its own (#197).
 *
 * **The message a person reads is built here from the code, never from the `ProblemDetail`'s
 * `detail`**: that field is English and written for a log, so showing it would put an English
 * sentence in front of somebody who chose Spanish — and would tie the wording of the interface to a
 * string the backend is free to reword.
 *
 * These do not go through the global mutation toast of `config/query.ts`. Every one of them is a
 * refusal of a form that is still open, and the answer belongs above the button that was just
 * pressed rather than in a toast that disappears in four seconds — the same reasoning that put the
 * upload errors under the dropzone.
 */
export const USER_ADMIN_ERROR_CODES = [
  /** An `Admin` reached for `Admin` or `Owner`. Not `400`: it is who you are, not what you sent. */
  'ROLE_GRANT_FORBIDDEN',
  /** The target holds `Admin` or `Owner`. Refused to everybody, the owner included. */
  'CANNOT_BLOCK_PRIVILEGED',
  /** The operation would leave the platform with no active owner. */
  'LAST_OWNER',
  /** An owner tried to take their own `Owner` away. */
  'CANNOT_REVOKE_OWN_OWNER',
  /** Blocking an account that is already blocked. */
  'USER_ALREADY_BLOCKED',
  /** Unblocking an account that is not blocked — a `Deleted` one included. */
  'USER_NOT_BLOCKED',
] as const

const KNOWN_CODES: ReadonlySet<string> = new Set(USER_ADMIN_ERROR_CODES)

/**
 * The translation key for whatever a `/admin/users` mutation failed with.
 *
 * Everything that is not one of the six — a `404`, a `500`, a backend that never answered — falls
 * back to one generic sentence. Guessing at a message for a code nobody committed to is how an
 * interface ends up asserting something the server never said.
 *
 * @param error what the mutation rejected with, or null while it has not failed
 * @returns the `admin` namespace key to render, or null when there is nothing to say
 */
export function userAdminErrorKey(error: unknown): string | null {
  if (error === null || error === undefined) return null
  if (error instanceof ApiError && KNOWN_CODES.has(error.problem.errorCode)) {
    return `users.errors.${error.problem.errorCode}`
  }
  return 'users.errors.generic'
}
