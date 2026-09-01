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

export type CreateGameTableForm = z.infer<typeof createGameTableSchema>

export const changeTableStatusSchema = z.object({
  justification: z.string().min(1).max(2000),
})

export type ChangeTableStatusForm = z.infer<typeof changeTableStatusSchema>

/** secondaryUserIds llega como texto separado por comas: todavía no hay un buscador de usuarios (E2 sub-rebanada 5). */
export const assignMastersSchema = z.object({
  primaryUserId: z.string().min(1),
  secondaryUserIds: z.string().max(500).optional(),
})

export type AssignMastersForm = z.infer<typeof assignMastersSchema>
