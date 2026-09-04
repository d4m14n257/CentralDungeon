import { z } from 'zod'

/**
 * Validates what a master is about to publish.
 *
 * Two of the rules are about the relation between fields rather than about any one of them, which is
 * why they are refinements and not per-field checks: a task has to take **something** back, and a
 * task addressed to one person has to name them. The backend refuses both as well (400) — this is
 * here so the person finds out while typing rather than after sending.
 *
 * The form uses `''` for "empty" and the API expects `null`; the conversion happens on submit, the
 * same way the application form does it.
 */
export const taskFormSchema = z
  .object({
    title: z.string().min(1).max(128),
    description: z.string().optional(),
    audience: z.enum(['Candidates', 'Players', 'Single']),
    targetUserId: z.string().optional(),
    tableSessionId: z.string().optional(),
    acceptsText: z.boolean(),
    acceptsFiles: z.boolean(),
    isMandatory: z.boolean(),
    /** `datetime-local`, in the reader's own zone. Converted to UTC on submit (#22, #192). */
    dueAtLocal: z.string().optional(),
  })
  .refine((values) => values.acceptsText || values.acceptsFiles, {
    // A task that takes neither text nor files asks for something nobody can hand in.
    path: ['acceptsText'],
    message: 'answerChannelRequired',
  })
  .refine((values) => values.audience !== 'Single' || Boolean(values.targetUserId), {
    // An audience of one that names nobody addresses nobody.
    path: ['targetUserId'],
    message: 'targetRequired',
  })

/** The task form's values, inferred from its schema. */
export type TaskForm = z.infer<typeof taskFormSchema>

/**
 * Validates an answer before it is handed in.
 *
 * It only checks that something is being sent: whether *this* task takes text or files is the task's
 * own shape, and the dialog already offers only the channels it opened. An answer empty on both
 * counts would be a row claiming somebody responded when they did not (#76).
 */
export const submissionFormSchema = z.object({
  content: z.string().optional(),
})

/** The answer form's values, inferred from its schema. */
export type SubmissionForm = z.infer<typeof submissionFormSchema>
