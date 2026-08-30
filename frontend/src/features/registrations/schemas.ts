import { z } from 'zod'

/** El formulario usa undefined para "vacío"; la API espera null - se convierte al enviar. */
export const createRegistrationSchema = z.object({
  description: z.string().max(2000).optional(),
})

export type CreateRegistrationForm = z.infer<typeof createRegistrationSchema>

export const rejectRegistrationSchema = z.object({
  justification: z.string().min(1).max(500),
})

export type RejectRegistrationForm = z.infer<typeof rejectRegistrationSchema>
