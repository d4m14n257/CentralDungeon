import { z } from 'zod'

/**
 * Builds the validation for one setting's form, bounded by that setting's own range.
 *
 * **A factory and not a constant schema**, which is the whole shape of this screen: the bound belongs
 * to the key, and every key has a different one — 1 to 25 megabytes, 1 to 1440 minutes, 1 to 3650
 * days. A single schema could only check "a whole number", and the person would learn the real limit
 * from a `400` after pressing, which is the "a limit you only meet by breaking it" principio 2
 * refuses.
 *
 * The server checks the same range again in `SettingsService` and answers `SETTING_OUT_OF_RANGE`
 * carrying both numbers (#197) — this is what makes the refusal immediate, not what makes it safe.
 *
 * `value` stays a string, like `maxPlayers` in `features/tables/schemas.ts` and for the same reason:
 * mixing `z.coerce.number()` with an optional field under `exactOptionalPropertyTypes` breaks the
 * resolver's type inference. The screen parses it once on submit.
 *
 * @param minValue the smallest value that key accepts, inclusive
 * @param maxValue the largest value that key accepts, inclusive
 * @returns the schema for that setting's form
 */
export function updateSettingSchema(minValue: number, maxValue: number) {
  return z.object({
    value: z
      .string()
      .min(1)
      .refine((raw) => /^\d+$/.test(raw.trim()), { params: { i18n: 'validation.wholeNumber' } })
      .refine(
        (raw) => {
          const parsed = Number(raw.trim())
          return parsed >= minValue && parsed <= maxValue
        },
        { params: { i18n: 'validation.between', minValue, maxValue } },
      ),
    justification: z.string().min(1).max(2000),
  })
}

/**
 * The edit form's values, inferred from the schema the factory builds.
 *
 * Inferred from a call with arbitrary bounds rather than written out by hand (regla dura 6): the
 * shape does not depend on the range, so declaring it separately would be a second source of truth
 * for two field names.
 */
export type UpdateSettingForm = z.infer<ReturnType<typeof updateSettingSchema>>
