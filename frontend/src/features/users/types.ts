import type { AttendanceSummary } from '@/types/api'

/** Espejo de UserDetailResponse. */
export interface User {
  id: string
  name: string | null
  country: string | null
  karma: number
  needsOnboarding: boolean
  roles: string[]
  hasManagedTables: boolean
}

/**
 * Mirror of UserSummaryResponse: how a person looks in a listing or in a picker. It is not derived
 * from `User` with a utility type because it is not a narrower view of the same thing — it carries
 * `discordUsername`, which `/users/me` does not return (arquitectura.md 2.3, 3.2).
 */
export interface UserSummary {
  id: string
  discordUsername: string
  name: string | null
}

/** What onboarding sends: the display name and country that unblock the rest of the app (#134). */
export interface CompleteOnboardingInput {
  name: NonNullable<User['name']>
  country: NonNullable<User['country']>
}

/**
 * Mirror of ProfileResponse — the upper half of a profile screen, for both `/player/profile` and
 * `/player/users/:id` (decisiones.md #248). Not derived from `User` with a utility type even though
 * `id`/`name`/`country`/`roles` line up: the two are answers to different questions with different
 * visibility rules (#41, #44, #47, #249) and `User` is only ever the caller describing themselves
 * (arquitectura.md 2.3) — a `Pick<User, …>` here would quietly imply this shape is also
 * self-only, which is exactly wrong for the id-keyed half of this type's job.
 *
 * **No `karma`, no `comments` — not even as `null`** (#248): those are F5, behind `KarmaService`,
 * and a field that traveled empty would teach this screen to expect a number that has nowhere to
 * come from yet.
 */
export interface Profile {
  id: string
  name: string | null
  country: string | null
  roles: string[]
  attendance: AttendanceSummary
}
