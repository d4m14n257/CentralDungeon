import { z } from 'zod'

/**
 * The form uses undefined/"" for "empty"; the API expects null - the conversion happens on submit.
 * maxPlayers/totalSessions stay strings (not z.coerce.number()): mixing coerce with .optional()
 * under exactOptionalPropertyTypes breaks the resolver's type inference.
 *
 * The catalogs and the agenda are **not** here: they are not text fields but collections the wizard
 * keeps in its own state and the server validates - catalog depth (#59), schedule clashes (#178).
 * Putting them in the schema would duplicate rules only the backend can decide.
 */
export const createGameTableSchema = z.object({
  name: z.string().min(1).max(128),
  description: z.string().max(10000).optional(),
  permitted: z.string().max(10000).optional(),
  requirements: z.string().max(10000).optional(),
  tableTypeId: z.string().optional(),
  /** `YYYY-MM-DDTHH:mm` exactly as an `<input type="datetime-local">` gives it, in local time. */
  startDate: z.string().optional(),
  /** `HH:mm` - how long **one** session lasts, not the campaign. */
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
 * The wizard's four steps, in order: identity → catalogs → agenda → capacity and review. One
 * decision per step (`fase-1-master.md` F1.2), and the summary before sending.
 *
 * A tuple of literals and not a `number`: `Record<WizardStep, …>` forces all four cases to be
 * covered when mapping to a title, which is the same reason the table statuses are a union
 * (§3.2 regla 9).
 */
export const WIZARD_STEPS = ['identity', 'catalogs', 'schedule', 'capacity'] as const

/** One of the four steps of the create-table wizard. */
export type WizardStep = (typeof WIZARD_STEPS)[number]

/** Validates the justification every denying transition needs: cancel, request changes, pause. */
export const changeTableStatusSchema = z.object({
  justification: z.string().min(1).max(2000),
})

/** The justification form's values, inferred from the schema. */
export type ChangeTableStatusForm = z.infer<typeof changeTableStatusSchema>
