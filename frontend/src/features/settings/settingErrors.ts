import { ApiError } from '@/types/api'

/**
 * The refusals `/admin/settings` can answer with, each with a sentence of its own (#197).
 *
 * **The message a person reads is built here from the code, never from the `ProblemDetail`'s
 * `detail`**: that field is English and written for a log, so showing it would put an English
 * sentence in front of somebody who chose Spanish.
 *
 * Short list on purpose. There is exactly one rule a setting can break — the range of its own key —
 * and the form already prevents it, so this surfaces for somebody calling the API another way or for
 * a bound that moved under an open tab.
 */
export const SETTING_ERROR_CODES = [
  /** The value is outside what that key allows. Carries `minValue` and `maxValue` as parameters. */
  'SETTING_OUT_OF_RANGE',
] as const

const KNOWN_CODES: ReadonlySet<string> = new Set(SETTING_ERROR_CODES)

/**
 * The translation key and parameters for whatever an update failed with.
 *
 * `SETTING_OUT_OF_RANGE` is useless without its numbers — "out of range" says nothing about what to
 * type instead — so the `errorParams` the backend sent travel with the key (#197). Everything else
 * falls back to one generic sentence: guessing at a message for a code nobody committed to is how an
 * interface ends up asserting something the server never said.
 *
 * @param error what the mutation rejected with, or null while it has not failed
 * @returns the `admin` namespace key and its interpolation values, or null when there is nothing to say
 */
export function settingErrorKey(error: unknown): { key: string; params: Record<string, string> } | null {
  if (error === null || error === undefined) return null
  if (error instanceof ApiError && KNOWN_CODES.has(error.problem.errorCode)) {
    return { key: `settings.errors.${error.problem.errorCode}`, params: error.problem.errorParams ?? {} }
  }
  return { key: 'settings.errors.generic', params: {} }
}
