import { ApiError } from '@/types/api'

/**
 * The one refusal the reservation adds (#100, #197).
 *
 * **It is a race a screen cannot prevent on its own**, which is exactly why it needs a sentence of
 * its own rather than the generic "no pudimos completar la acción". Two admins with the tray open is
 * the normal case, not the edge one: the whole mechanism exists because that happens.
 *
 * **There was a second code here, `ITEM_NOT_CLAIMED`, and it was a mistake of the rule rather than of
 * the code.** The rule it belonged to said resolving required holding the item, which made every
 * resolution from `/admin/requests` — a screen with no way to reserve anything — answer `409` for a
 * reservation it could not offer. What #100 actually buys is "if one takes it, it drops for the
 * rest": what has to be refused is acting on what **somebody else** holds. An item nobody holds is
 * not that situation, and resolving it reserves it implicitly. No branch can emit `ITEM_NOT_CLAIMED`
 * any more, so it is gone from here too — a code the backend cannot send is a sentence nobody can
 * read and a test that proves nothing.
 *
 * **The message a person reads is built here from the code, never from the `ProblemDetail`'s
 * `detail`**: that field is English and written for a log, so showing it would put an English
 * sentence in front of somebody who chose Spanish — and would tie the wording of the interface to a
 * string the backend is free to reword.
 *
 * **Where the sentence goes is the caller's** and it is not the same everywhere: a refused resolution
 * is answered inside the dialog that is still open, over the button that was pressed, while a refused
 * claim has no form to sit in — the press was on the row — so it goes to a toast, which is where the
 * reader is already looking.
 */
export const ADMIN_QUEUE_ERROR_CODES = [
  /**
   * A colleague holds it: reserving it, releasing it, or resolving it are all refused the same way.
   *
   * **One code for the three acts, because it is one fact**: somebody else is working on this. The
   * polling of `config/query.ts` closes most of the window and cannot close all of it — fifteen
   * seconds is fifteen seconds, and the press can land inside them.
   */
  'ITEM_ALREADY_CLAIMED',
] as const

const KNOWN_CODES: ReadonlySet<string> = new Set(ADMIN_QUEUE_ERROR_CODES)

/**
 * The translation key for whatever a tray mutation failed with.
 *
 * Everything else — a `404`, a `500`, a backend that never answered — falls back to one generic
 * sentence. Guessing at a message for a code nobody committed to is how an interface ends up
 * asserting something the server never said.
 *
 * @param error what the mutation rejected with, or null while it has not failed
 * @returns the `admin` namespace key to render, or null when there is nothing to say
 */
export function adminQueueErrorKey(error: unknown): string | null {
  if (error === null || error === undefined) return null
  if (error instanceof ApiError && KNOWN_CODES.has(error.problem.errorCode)) {
    return `queue.errors.${error.problem.errorCode}`
  }
  return 'queue.errors.generic'
}
