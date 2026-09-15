import type { AccountStatus, PlatformRole } from './types'

/**
 * The four platform roles, **in the order of #165** — the same order `PlatformRole` declares them in
 * and the same order the API returns them in.
 *
 * The order is read as increasing scope: the two anybody can hold, then the two that are one rank at
 * two scopes (#169). Chips, the role picker of the change dialog and the `/role` command all take
 * their order from here, so a listing and a dialog can never disagree about it.
 *
 * A written-out tuple rather than something derived from the union, because a union of literals has
 * no runtime value to iterate. `satisfies` is what makes a typo here a compile error.
 */
export const PLATFORM_ROLES = ['Player', 'Master', 'Admin', 'Owner'] as const satisfies readonly PlatformRole[]

/**
 * The two roles an `Admin` may grant and revoke (§3 of docs/fase-3-admin-owner.md).
 *
 * **The whole difference between an admin and an owner in F3, in one line**: everything else on the
 * administration surface is shared, and what separates them is who may hand out the rank.
 */
export const ADMIN_GRANTABLE_ROLES = ['Player', 'Master'] as const satisfies readonly PlatformRole[]

/**
 * The roles that make an account privileged — the ones that cannot be blocked, by anybody.
 *
 * Between peers there is no authority (§3): an admin does not block an admin and an owner does not
 * block an owner, and generalising it to "nobody blocks anybody holding these" covers self-blocking
 * for free, since the actor always holds one of the two.
 */
export const PRIVILEGED_ROLES = ['Admin', 'Owner'] as const satisfies readonly PlatformRole[]

/**
 * Every account status, in the order of the account's life.
 *
 * `Deleted` is offered to the `/status` search command because `/admin/users` can see one, and
 * withheld from every action: F3.1 neither produces it nor undoes it.
 */
export const ACCOUNT_STATUSES = ['Allowed', 'Blocked', 'Deleted'] as const satisfies readonly AccountStatus[]

/**
 * Whether an account holds a role that puts it out of reach of a block.
 *
 * It is written once here rather than inline in the capability hook, because the search box, the
 * listing and the hook all ask the same question and a second copy of it is how they start to
 * disagree.
 *
 * @param roles the account's active roles
 * @returns whether blocking it is refused to everybody (#84, §3)
 */
export function isPrivileged(roles: readonly PlatformRole[]): boolean {
  return roles.some((role) => (PRIVILEGED_ROLES as readonly PlatformRole[]).includes(role))
}
