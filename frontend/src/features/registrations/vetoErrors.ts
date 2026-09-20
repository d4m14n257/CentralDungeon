import { ApiError } from '@/types/api'

/**
 * The three refusals the veto adds, each with a sentence of its own (#197, F3.4).
 *
 * **Built from the code and never from the `ProblemDetail`'s `detail`**, which is English and
 * written for a log — showing it would put an English sentence in front of somebody who chose
 * Spanish, and would tie the interface's wording to a string the backend is free to reword.
 *
 * All three are refusals the screen already prevents, and they exist for the race it cannot: two
 * masters acting on the same row at once, or a tab left open while the other one acted. That is
 * precisely why the sentence matters — the reader did nothing wrong and needs to be told what
 * changed underneath them, not "no pudimos completar la acción".
 */
export const VETO_ERROR_CODES = [
  /** A co-master reaching the `Primary`-only act. It is who you are, not what you sent — hence 403. */
  'NOT_PRIMARY_MASTER',
  /** Vetoing somebody a colleague vetoed a moment ago. */
  'REGISTRATION_ALREADY_BLOCKED',
  /** Lifting a veto off a row that is not vetoed any more. */
  'REGISTRATION_NOT_BLOCKED',
] as const

const KNOWN_CODES: ReadonlySet<string> = new Set(VETO_ERROR_CODES)

/**
 * The translation key for whatever a veto mutation failed with, in the `registrations` namespace.
 *
 * Everything else — a `404`, a `500`, a backend that never answered — falls back to one generic
 * sentence. Guessing at a message for a code nobody committed to is how an interface ends up
 * asserting something the server never said.
 *
 * @param error what the mutation rejected with, or null while it has not failed
 * @returns the key to render, or null when there is nothing to say
 */
export function vetoErrorKey(error: unknown): string | null {
  if (error === null || error === undefined) return null
  if (error instanceof ApiError && KNOWN_CODES.has(error.problem.errorCode)) {
    return `veto.errors.${error.problem.errorCode}`
  }
  return 'veto.errors.generic'
}
