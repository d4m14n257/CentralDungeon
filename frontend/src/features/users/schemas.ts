import { z } from 'zod'

export const completeOnboardingSchema = z.object({
  name: z.string().min(1).max(64),
  country: z.string().regex(/^[A-Z]{2}$/),
})

export type CompleteOnboardingForm = z.infer<typeof completeOnboardingSchema>
