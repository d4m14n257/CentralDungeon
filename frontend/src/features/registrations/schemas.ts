import { z } from 'zod'

/** El formulario usa undefined para "vacío"; la API espera null - se convierte al enviar. */
export const createRegistrationSchema = z.object({
  description: z.string().max(2000).optional(),
})

/** The application form's values, inferred from its schema. */
export type CreateRegistrationForm = z.infer<typeof createRegistrationSchema>

/**
 * Validates a rejection. The justification is required here as well as on the backend: a rejection
 * the applicant can learn nothing from is the worst outcome this flow produces.
 */
export const rejectRegistrationSchema = z.object({
  justification: z.string().min(1).max(500),
})

/** The rejection form's values, inferred from the schema. */
export type RejectRegistrationForm = z.infer<typeof rejectRegistrationSchema>
