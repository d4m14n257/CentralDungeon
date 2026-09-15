import type { StrictOmit } from '@/types/utils'
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

/**
 * The four platform roles, spelled as the API spells them — a union of literals and not a TS `enum`
 * (arquitectura.md §3.2). Mirror of `PlatformRole.roleName()`.
 *
 * They stack and there is no hierarchy among them (#37, #89), with one exception written in the
 * service and not here: `Admin` and `Owner` never coexist, because they are one rank at two scopes
 * (#169).
 */
export type PlatformRole = 'Player' | 'Master' | 'Admin' | 'Owner'

/**
 * The state of an account, as `users.status` spells it. `Deleted` is not something F3.1 produces or
 * undoes: it is here because `/admin/users` is the one screen that can see every account.
 */
export type AccountStatus = 'Allowed' | 'Blocked' | 'Deleted'

/**
 * Mirror of `AdminUserDetailResponse` — a person as `/admin/users` sees them, which is the only
 * screen that sees blocked accounts at all.
 *
 * **The base type of the admin listing**, with the summary derived from it below rather than the
 * other way round: the detail is what the six mutators answer with, so it is the shape the screen
 * writes back into its cache (arquitectura.md §3.2, regla dura 12).
 *
 * **No `discordId`** — administering somebody never needs it, and it is a third party's identifier.
 */
export interface AdminUserDetail {
  id: string
  discordUsername: string
  name: string | null
  country: string | null
  status: AccountStatus
  /** Only the roles that are active, in the order of #165: Player, Master, Admin, Owner. */
  roles: PlatformRole[]
  createdAt: string
  /**
   * Nullable, because `AdminUserDetailResponse` declares it so: an account nobody has ever touched
   * has never been updated, and `BaseEntity` leaves the column null until something writes to it.
   */
  updatedAt: string | null
}

/**
 * Mirror of `AdminUserSummaryResponse`: one row of the `/admin/users` table. Derived from
 * {@link AdminUserDetail} because it is exactly the narrower view of the same thing — the listing
 * does not carry `updatedAt`, and nothing else differs.
 */
export type AdminUserSummary = StrictOmit<AdminUserDetail, 'updatedAt'>

/** What kind of change one row of a person's administration history records. */
export type UserAdminChangeType = 'RoleGranted' | 'RoleRevoked' | 'StatusChanged'

/**
 * Mirror of `UserAdminChangeResponse`: one entry of what admins did to an account, and why.
 *
 * The two tables behind it — `user_role_changes` and `user_status_changes` — arrive merged into one
 * list, so `role` is filled on the two role kinds and the two statuses on the third. It carries
 * `changedByName` and never the id, the same as a table's status history: a history nobody can read
 * without a second lookup is a history nobody reads.
 *
 * **The justification is never optional** (#84, #169): every row here was written by somebody who
 * had to say why.
 */
export interface UserAdminChange {
  id: string
  type: UserAdminChangeType
  role: PlatformRole | null
  fromStatus: AccountStatus | null
  toStatus: AccountStatus | null
  changedByName: string
  justification: string
  createdAt: string
}

/**
 * What granting or revoking a role sends: which role, and why. Both endpoints take the same body, so
 * it is one type — `RevokeRoleRequest` and `GrantRoleRequest` are separate records on the server
 * because a request record is never shared there (regla dura 3), which is a statement about Java's
 * side of the wire and not about this one.
 */
export interface ChangeUserRoleInput {
  role: PlatformRole
  justification: string
}

/**
 * What blocking or unblocking an account sends. Derived from {@link ChangeUserRoleInput}: it is the
 * same justification, without a role to apply it to.
 */
export type ChangeUserStatusInput = StrictOmit<ChangeUserRoleInput, 'role'>
