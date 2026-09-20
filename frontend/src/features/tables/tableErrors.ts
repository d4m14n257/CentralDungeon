import { ApiError } from '@/types/api'

/**
 * A refusal already reduced to what a screen needs: which sentence, and what it interpolates.
 *
 * **The parameters are the reason this is a pair and not a bare key.** Every other error helper in
 * the project (`approvalErrors`, `adminErrors`) answers with a key alone, because their sentences
 * are fixed. The clash is not: its whole value is the name of the table it collides with, and a key
 * with nowhere to put that name would render "se pisa con «»".
 */
export interface TableErrorMessage {
  /** The key to render, in the `admin` namespace. */
  key: string
  /** What the sentence interpolates. Empty for the refusals whose text has no placeholder. */
  params: Record<string, string>
}

/** What both acts fall back to: a refusal nobody committed to a code for. */
const GENERIC: TableErrorMessage = { key: 'tables.errors.generic', params: {} }

/**
 * The sentence to show over a refused pause or resume (#193, #197).
 *
 * **`SCHEDULE_CONFLICT` is why this exists.** Resuming re-checks the clash before moving anything,
 * because the master may have committed to another table while this one was frozen — and the backend
 * answers naming that table in `errorParams.otherTableName`. A generic "no pudimos completar la
 * acción" would throw away the single fact that makes the problem solvable: the admin has to know
 * *which* table to look at before they can do anything about it (principio 2).
 *
 * **It is not the sentence `config/query.ts` already has for that code**, deliberately. The global
 * one is written for the person whose own week clashed — *"donde ya estás comprometido"* — and the
 * admin resuming a table is not that person: what collided is the **master's** agenda, and a
 * sentence that tells an admin they are committed to a table they have never heard of is worse than
 * a vague one. Same reasoning that gave `CANDIDATE_SCHEDULE_CONFLICT` a code of its own.
 *
 * Anything else — a `403`, a `500`, a backend that never answered — falls back to one generic
 * sentence. Inventing a message for a code nobody committed to is how an interface ends up asserting
 * something the server never said.
 *
 * @param error what the mutation rejected with, or null while it has not failed
 * @returns the sentence to render, or null when there is nothing to say
 */
export function tableActionErrorMessage(error: unknown): TableErrorMessage | null {
  if (error === null || error === undefined) return null
  if (!(error instanceof ApiError)) return GENERIC
  if (error.problem.errorCode !== 'SCHEDULE_CONFLICT') return GENERIC
  const otherTableName = error.problem.errorParams?.['otherTableName']
  // The code without its parameter is the one case where the specific sentence cannot be written:
  // it is "se pisa con «»", which says less than the generic one.
  if (otherTableName === undefined || otherTableName === '') return GENERIC
  return { key: 'tables.errors.SCHEDULE_CONFLICT', params: { otherTableName } }
}

/**
 * The sentence to show over a refused **request** for a pause (#32, #197).
 *
 * **Its own function, in the `master` namespace**, because it answers a different person on a
 * different screen: `tableActionErrorMessage` above speaks to the admin pausing from `/admin/tables`
 * and its keys live in `admin`. `PAUSE_ALREADY_REQUESTED` cannot even reach that screen — the admin
 * pauses through `POST /{id}/pause`, which never raises it. One code, one caller, one vocabulary.
 *
 * **The screen prevents this and still has to explain it**, which is the shape every refusal of this
 * kind has: the button is gone once the table sits in `PauseRequested`, so the only way to arrive
 * here is a tab left open from before somebody else asked — a co-master, or the same master in a
 * second window. "No pudimos completar la acción" sends that person to press again; saying the pause
 * is already waiting sends them to reload and see it.
 *
 * @param error what the mutation rejected with, or null while it has not failed
 * @returns the `master` namespace key to render, or null when there is nothing to say
 */
export function pauseRequestErrorKey(error: unknown): string | null {
  if (error === null || error === undefined) return null
  if (error instanceof ApiError && error.problem.errorCode === 'PAUSE_ALREADY_REQUESTED') {
    return 'status.errors.PAUSE_ALREADY_REQUESTED'
  }
  return 'status.errors.generic'
}
