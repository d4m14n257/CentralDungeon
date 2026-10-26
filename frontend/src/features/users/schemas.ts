import { z } from 'zod'

import { PLATFORM_ROLES } from './roles'
import type { ChangeUserRoleInput, ChangeUserStatusInput } from './types'
import type { Equals, Expect } from '@/types/utils'

/** Validates the onboarding form: a display name and a country, both required (ADR-10 §4). */
export const completeOnboardingSchema = z.object({
  name: z.string().min(1).max(64),
  // min(1) first: an empty country reads as "required", not as a malformed code.
  country: z
    .string()
    .min(1)
    .regex(/^[A-Z]{2}$/),
})

/**
 * The onboarding form's values, inferred from the schema. Tied to `CompleteOnboardingInput` by a
 * compile-time assertion below, so form and payload cannot drift apart (arquitectura §3.2, rule 7).
 */
export type CompleteOnboardingForm = z.infer<typeof completeOnboardingSchema>

/**
 * Validates a role change: which role, and why (ADR-06 §1).
 *
 * **The justification is required on this side too, and not only on the server.** The backend
 * answers `VALIDATION_ERROR` to an empty one, but a round trip is a bad way to learn that a textarea
 * was blank — and `@NotBlank` means whitespace is not a reason either, which `.trim().min(1)` says
 * here in the same words.
 *
 * The role is checked against the four the platform has rather than against what the dialog offered:
 * which roles this particular reader may hand out is a capability question, answered by
 * `useUserAdminCapabilities`, and folding it in here would make the schema depend on who is looking.
 */
export const changeUserRoleSchema = z.object({
  role: z.enum(PLATFORM_ROLES),
  justification: z.string().trim().min(1).max(2000),
})

/** The role-change form's values, inferred from the schema. */
export type ChangeUserRoleForm = z.infer<typeof changeUserRoleSchema>

/** Validates a block or an unblock: the reason, which is all either of them sends (ADR-05 §7). */
export const changeUserStatusSchema = z.object({
  justification: z.string().trim().min(1).max(2000),
})

/** The block/unblock form's values, inferred from the schema. */
export type ChangeUserStatusForm = z.infer<typeof changeUserStatusSchema>

/**
 * Form and payload cannot drift apart (arquitectura §3.2, regla 7): if the API's input type gains
 * a field or renames one, these stop compiling instead of failing at runtime.
 */
export type RoleFormMatchesPayload = Expect<Equals<ChangeUserRoleForm, ChangeUserRoleInput>>
/** As above, for the block and unblock forms. Never referenced at runtime. */
export type StatusFormMatchesPayload = Expect<Equals<ChangeUserStatusForm, ChangeUserStatusInput>>
