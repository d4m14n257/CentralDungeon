import { ApiError } from '@/types/api'

/**
 * The four refusals F3.2 adds, each with a sentence of its own (#197).
 *
 * **The message a person reads is built here from the code, never from the `ProblemDetail`'s
 * `detail`**: that field is English and written for a log, so showing it would put an English
 * sentence in front of somebody who chose Spanish — and would tie the wording of the interface to a
 * string the backend is free to reword.
 *
 * These do not go through the global mutation toast of `config/query.ts`. Every one of them is a
 * refusal of a form that is still open, and the answer belongs above the button that was just
 * pressed rather than in a toast that disappears in four seconds — the same reading as
 * `features/users/adminErrors.ts`.
 *
 * Three of the four are refusals the screen already prevents: it does not offer a second request of
 * a kind that is still pending, nor the master role to somebody who holds it, nor a resolution on a
 * request that is no longer `Pending`. They surface for whoever reaches the API another way, and for
 * the race between two admins resolving the same row — which is exactly the case a screen cannot
 * prevent on its own.
 */
export const APPROVAL_ERROR_CODES = [
  /** A second request of the same kind, by the same person, while the first is still waiting. */
  'REQUEST_ALREADY_PENDING',
  /** Approving or rejecting something that somebody else already resolved. */
  'REQUEST_ALREADY_RESOLVED',
  /** What the request pointed at is gone (#78): the row outlived its reference, so it cannot be acted on. */
  'REQUEST_ENTITY_GONE',
  /** Asking for the master role while already holding it. */
  'MASTER_ROLE_ALREADY_HELD',
  /**
   * Resolving a request another admin has taken from the shared tray (#100, F3.3).
   *
   * **This screen has no way to reserve anything and does not need one**: resolving something nobody
   * holds reserves it implicitly, which is the whole of the correction F3.3 made to the rule. What is
   * still refused is resolving what a colleague is already working on — and `/admin/requests` lists
   * every request there is, including the ones somebody took, so unlike `/admin/queue` this screen
   * really can show a row it cannot resolve.
   */
  'ITEM_ALREADY_CLAIMED',
] as const

const KNOWN_CODES: ReadonlySet<string> = new Set(APPROVAL_ERROR_CODES)

/**
 * Where the reservation's own sentence lives.
 *
 * **Pointed at rather than copied.** `ITEM_ALREADY_CLAIMED` is not this feature's vocabulary — the
 * reservation belongs to the shared tray, and the same refusal reaches `/admin/queue` and
 * `/admin/requests` about the same fact. Writing it twice, once per screen, is how two sentences that
 * mean one thing start to disagree (#176). It is a key in the same `admin` namespace both screens
 * translate against, not an import: `features/approvals` still imports nothing from
 * `features/adminQueue` (§3.1.5).
 */
const CLAIM_ERROR_KEY = 'queue.errors.ITEM_ALREADY_CLAIMED'

/**
 * The translation key for whatever a request mutation failed with.
 *
 * Everything that is not one of the five — a `404`, a `500`, a backend that never answered — falls
 * back to one generic sentence. Guessing at a message for a code nobody committed to is how an
 * interface ends up asserting something the server never said.
 *
 * @param error what the mutation rejected with, or null while it has not failed
 * @returns the `admin` namespace key to render, or null when there is nothing to say
 */
export function approvalErrorKey(error: unknown): string | null {
  if (error === null || error === undefined) return null
  if (error instanceof ApiError && error.problem.errorCode === 'ITEM_ALREADY_CLAIMED') {
    return CLAIM_ERROR_KEY
  }
  if (error instanceof ApiError && KNOWN_CODES.has(error.problem.errorCode)) {
    return `requests.errors.${error.problem.errorCode}`
  }
  return 'requests.errors.generic'
}
