import { z } from 'zod'

/**
 * El formulario usa undefined/"" para "vacío"; la API espera null - se convierte al enviar.
 * maxPlayers/totalSessions quedan como string (no z.coerce.number()): mezclar coerce con
 * .optional() bajo exactOptionalPropertyTypes rompe la inferencia de tipos del resolver.
 *
 * Los catálogos y la agenda **no** están acá: no son campos de texto sino colecciones que el wizard
 * maneja con su propio estado y que valida el servidor —profundidad de catálogo (#59), choque de
 * horarios (#178)—. Meterlos en el esquema duplicaría reglas que solo el backend puede decidir.
 */
export const createGameTableSchema = z.object({
  name: z.string().min(1).max(128),
  description: z.string().max(10000).optional(),
  permitted: z.string().max(10000).optional(),
  requirements: z.string().max(10000).optional(),
  tableTypeId: z.string().optional(),
  /** `YYYY-MM-DDTHH:mm` tal como lo entrega un `<input type="datetime-local">`, en hora local. */
  startDate: z.string().optional(),
  /** `HH:mm` - cuánto dura **una** sesión, no la campaña. */
  duration: z.string().optional(),
  maxPlayers: z.string().optional(),
  totalSessions: z.string().optional(),
})

/**
 * The create-table form's values, inferred from its schema and tied to `CreateGameTableRequest` by a
 * compile-time assertion (#3.2 regla 7).
 */
export type CreateGameTableForm = z.infer<typeof createGameTableSchema>

/**
 * Los cuatro pasos del wizard, en orden: identidad → catálogos → agenda → cupo y revisión. Una
 * decisión por paso (`fase-1-master.md` F1.2), y el resumen antes de enviar.
 *
 * Es una tupla de literales y no un `number`: `Record<WizardStep, …>` obliga a cubrir los cuatro
 * casos al mapear a un título, que es la misma razón por la que los estados de mesa son una unión
 * (§3.2 regla 9).
 */
export const WIZARD_STEPS = ['identity', 'catalogs', 'schedule', 'capacity'] as const

/** Uno de los cuatro pasos del wizard de creación. */
export type WizardStep = (typeof WIZARD_STEPS)[number]

/** Validates the justification every denying transition needs: cancel, request changes, pause. */
export const changeTableStatusSchema = z.object({
  justification: z.string().min(1).max(2000),
})

/** The justification form's values, inferred from the schema. */
export type ChangeTableStatusForm = z.infer<typeof changeTableStatusSchema>
