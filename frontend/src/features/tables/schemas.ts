import { z } from 'zod'

/**
 * El formulario usa undefined/"" para "vacío"; la API espera null - se convierte al enviar.
 * maxPlayers/totalSessions quedan como string (no z.coerce.number()): mezclar coerce con
 * .optional() bajo exactOptionalPropertyTypes rompe la inferencia de tipos del resolver.
 */
export const createGameTableSchema = z.object({
  name: z.string().min(1).max(128),
  description: z.string().max(10000).optional(),
  requirements: z.string().max(10000).optional(),
  maxPlayers: z.string().optional(),
  totalSessions: z.string().optional(),
})

/**
 * The create-table form's values, inferred from its schema and tied to `CreateGameTableRequest` by a
 * compile-time assertion (#3.2 regla 7).
 */
export type CreateGameTableForm = z.infer<typeof createGameTableSchema>

/** Validates the justification every denying transition needs: cancel, request changes, pause. */
export const changeTableStatusSchema = z.object({
  justification: z.string().min(1).max(2000),
})

/** The justification form's values, inferred from the schema. */
export type ChangeTableStatusForm = z.infer<typeof changeTableStatusSchema>
