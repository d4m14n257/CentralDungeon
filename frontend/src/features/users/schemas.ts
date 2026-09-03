import { z } from 'zod'

/** Validates the onboarding form: a display name and a country, both required (#134). */
export const completeOnboardingSchema = z.object({
  name: z.string().min(1).max(64),
  country: z.string().regex(/^[A-Z]{2}$/),
})

/**
 * The onboarding form's values, inferred from the schema. Tied to `CompleteOnboardingInput` by a
 * compile-time assertion below, so form and payload cannot drift apart (#3.2 regla 7).
 */
export type CompleteOnboardingForm = z.infer<typeof completeOnboardingSchema>
