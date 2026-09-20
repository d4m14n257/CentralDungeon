import { z } from 'zod'

import { SUBMITTABLE_REQUEST_TYPES } from './requestTypes'
import type { ResolveApprovalRequestInput, SubmitApprovalRequestInput } from './types'
import type { Equals, Expect } from '@/types/utils'

/**
 * Validates a request before it is sent: which kind, and why (#42).
 *
 * **The justification is required on this side too**, not only on the server. The backend answers
 * `VALIDATION_ERROR` to an empty one, but a round trip is a bad way to learn that a textarea was
 * blank — and `@NotBlank` means whitespace is not a reason either, which `.trim().min(1)` says here
 * in the same words.
 *
 * The 4000 cap is the server's `@Size`, repeated rather than guessed: the column is `LONGTEXT`, so
 * without it a four-megabyte body travels the whole way before being refused.
 */
export const submitApprovalRequestSchema = z.object({
  // The submittable three and not all five: the two F3.4 added are raised from their own entity's
  // route, because `POST /requests` carries no `entityId` on purpose (F3.2 §0d).
  type: z.enum(SUBMITTABLE_REQUEST_TYPES),
  justification: z.string().trim().min(1).max(4000),
})

/** The request form's values, inferred from the schema. */
export type SubmitApprovalRequestForm = z.infer<typeof submitApprovalRequestSchema>

/**
 * Validates a resolution: the note, which both approving and rejecting send.
 *
 * **Obligatory in the two acts** (#42). The mechanism asks for a reason at both ends, and a
 * rejection nobody explained is the half that makes the other half worthless.
 */
export const resolveApprovalRequestSchema = z.object({
  resolutionNote: z.string().trim().min(1).max(4000),
})

/** The resolution form's values, inferred from the schema. */
export type ResolveApprovalRequestForm = z.infer<typeof resolveApprovalRequestSchema>

/**
 * Form and payload cannot drift apart (arquitectura.md §3.2, regla 7): if the API's input type gains
 * a field or renames one, these stop compiling instead of failing at runtime.
 */
export type SubmitFormMatchesPayload = Expect<Equals<SubmitApprovalRequestForm, SubmitApprovalRequestInput>>
/** As above, for the two resolution acts. Never referenced at runtime. */
export type ResolveFormMatchesPayload = Expect<Equals<ResolveApprovalRequestForm, ResolveApprovalRequestInput>>
