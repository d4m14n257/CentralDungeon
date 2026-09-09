import { z } from 'zod'

import i18n from '@/providers/i18n'

/**
 * Translates a key only if it exists, so a rule with no key yet falls through to zod's own text
 * instead of showing `validation.something` to whoever is filling in the form.
 */
function translated(key: string, values?: Record<string, unknown>): string | undefined {
  return i18n.exists(key) ? i18n.t(key, values ?? {}) : undefined
}

/**
 * Makes zod speak the reader's language (#226).
 *
 * Out of the box a failed rule reads "Too small: expected string to have >=1 characters" — English,
 * written by the library, and shown straight to whoever is filling in the form by `FormMessage`.
 * That is the same defect regla dura 18 forbids everywhere else, and it slipped in because nobody
 * had looked at a form with the language switched.
 *
 * It is one global map rather than a message on every rule: a schema describes what is valid, and
 * having to remember a `t()` on each `.min()` is exactly how half of them end up in English again.
 * Every zod schema in the application is covered, including the ones nobody has written yet.
 *
 * The lookup happens **when the rule fails**, not when the schema is built, so a schema defined at
 * module load still produces its message in whatever language is current at that moment.
 *
 * Returning `undefined` hands the message back to zod, which is what keeps an untranslated rule
 * readable rather than turning it into a key.
 */
export function installZodErrorMap(): void {
  z.config({
    customError: (issue) => {
      switch (issue.code) {
        case 'invalid_type':
          // A required field left empty arrives here when the value is missing altogether.
          return translated('validation.required')
        case 'too_small':
          // "at least one character" is the empty field, not a length anybody needs to be told about.
          if (issue.origin === 'string' && Number(issue.minimum) === 1) {
            return translated('validation.required')
          }
          return translated(`validation.tooSmall.${issue.origin}`, { count: Number(issue.minimum) })
        case 'too_big':
          return translated(`validation.tooBig.${issue.origin}`, { count: Number(issue.maximum) })
        case 'invalid_format':
          return translated(`validation.format.${issue.format}`) ?? translated('validation.invalid')
        default:
          return translated('validation.invalid')
      }
    },
  })
}
