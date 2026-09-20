import { z } from 'zod'

/** The form uses undefined for "empty"; the API expects null - the conversion happens on submit. */
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

/**
 * Validates a veto, its lifting, and a co-master asking for one (#39, F3.4).
 *
 * **The reason is obligatory on all three, and that is the whole mechanism.** #39 does not ask for
 * it to be polite: a veto is reversible, and reversing one means somebody later reading why it was
 * applied. A `Blocked` row with no reason is a decision nobody can revisit — and, across a platform,
 * it is what makes a pattern of impulsive vetoes invisible.
 *
 * The 4000 cap is the server's `@Size` on `BlockRegistrationRequest`, repeated rather than guessed:
 * the column is `LONGTEXT`, so without it a four-megabyte body travels the whole way before being
 * refused. `.trim()` says the same thing `@NotBlank` does — whitespace is not a reason.
 */
export const blockRegistrationSchema = z.object({
  justification: z.string().trim().min(1).max(4000),
})

/** The veto form's values, inferred from the schema. Shared by the three acts (#39). */
export type BlockRegistrationForm = z.infer<typeof blockRegistrationSchema>
