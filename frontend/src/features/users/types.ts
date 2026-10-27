import type { StrictOmit } from '@/types/utils'
import type { AttendanceSummary } from '@/types/api'

/** Espejo de UserDetailResponse. */
export interface User {
  id: string
  name: string | null
  country: string | null
  needsOnboarding: boolean
  roles: string[]
  hasManagedTables: boolean
}

/**
 * Mirror of UserSummaryResponse: how a person looks in a listing or in a picker. It is not derived
 * from `User` with a utility type because it is not a narrower view of the same thing — it carries
 * `discordUsername`, which `/users/me` does not return (arquitectura §2.3, 3.2).
 */
export interface UserSummary {
  id: string
  discordUsername: string
  name: string | null
}

/** What onboarding sends: the display name and country that unblock the rest of the app (ADR-10 §4). */
export interface CompleteOnboardingInput {
  name: NonNullable<User['name']>
  country: NonNullable<User['country']>
}

/**
 * Mirror of ProfileResponse — the upper half of a profile screen, for both `/player/profile` and
 * `/player/users/:id` (ADR-10 §5). Not derived from `User` with a utility type even though
 * `id`/`name`/`country`/`roles` line up: the two are answers to different questions with different
 * visibility rules (ADR-10 §1, §2, §3) and `User` is only ever the caller describing themselves
 * (arquitectura §2.3) — a `Pick<User, …>` here would quietly imply this shape is also
 * self-only, which is exactly wrong for the id-keyed half of this type's job.
 *
 * **No `karma`, no `comments` — not even as `null`** (ADR-10 §5): those are F5, behind `KarmaService`,
 * and a field that traveled empty would teach this screen to expect a number that has nowhere to
 * come from yet.
 */
export interface Profile {
  id: string
  /** Their Discord handle: how the community finds them on the server (ADR-06 §8). */
  discordUsername: string
  name: string | null
  country: string | null
  roles: string[]
  attendance: AttendanceSummary
  /** How many tables they played to the end, as a player (ADR-06 §8). Their track record without karma (ADR-10 §5). */
  finishedTables: number
}

/** What a person is to a table, as an admin reads it on their record (ADR-06 §8). Mirror of `UserTableResponse.relation`. */
export type UserTableRelation = 'Primary' | 'Secondary' | 'Player' | 'Candidate' | 'Rejected' | 'Blocked'

/**
 * Mirror of `UserTableResponse`: one table a person is linked to, on an admin's view of their record
 * (ADR-06 §8). One row per table - nobody runs and plays the same one (ADR-06 §5).
 */
export interface UserTable {
  tableId: string
  tableName: string
  /** The table's status, as the backend names it; the screen badges it with the tables feature's words. */
  tableStatus: string
  relation: UserTableRelation
  /** When the table was created: the list comes newest first. */
  tableCreatedAt: string
}

/**
 * The four platform roles, spelled as the API spells them — a union of literals and not a TS `enum`
 * (arquitectura §3.2). Mirror of `PlatformRole.roleName()`.
 *
 * They stack and there is no hierarchy among them (ADR-06 §1), with one exception written in the
 * service and not here: `Admin` and `Owner` never coexist, because they are one rank at two scopes
 * (ADR-06 §1).
 */
export type PlatformRole = 'Player' | 'Master' | 'Admin' | 'Owner'

/**
 * The state of an account, as `users.status` spells it.
 */
export type AccountStatus = 'Allowed' | 'Blocked'

/**
 * Mirror of `AdminUserDetailResponse` — a person as `/admin/users` sees them, which is the only
 * screen that sees blocked accounts at all.
 *
 * **The base type of the admin listing**, with the summary derived from it below rather than the
 * other way round: the detail is what the six mutators answer with, so it is the shape the screen
 * writes back into its cache (arquitectura §3.2, regla dura 12).
 *
 * **No `discordId`** — administering somebody never needs it, and it is a third party's identifier.
 */
export interface AdminUserDetail {
  id: string
  discordUsername: string
  name: string | null
  country: string | null
  status: AccountStatus
  /** Only the roles that are active, in the order of ADR-07 §8: Player, Master, Admin, Owner. */
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
 * **The justification is never optional** (ADR-05 §7, ADR-06 §1): every row here was written by somebody who
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
