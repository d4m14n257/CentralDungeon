import { useMe } from '../api/useMe'
import { ADMIN_GRANTABLE_ROLES, PLATFORM_ROLES, isPrivileged } from '../roles'
import type { AdminUserSummary, PlatformRole } from '../types'

/** What `/admin/users` is allowed to offer the person reading it. */
export interface UserAdminCapabilities {
  /**
   * The roles this reader may hand out and take back, in the order of #165. Empty for anybody who is
   * neither an admin nor the owner, and empty until the profile arrives.
   */
  grantableRoles: readonly PlatformRole[]
  /**
   * Whether this reader may change the given account's status at all — block it or unblock it.
   *
   * @param user the account the row is about
   */
  canChangeStatus: (user: Pick<AdminUserSummary, 'roles' | 'status'>) => boolean
  /** Until the profile lands the answers are "no". Anything that *acts* on them has to wait. */
  isPending: boolean
}

/**
 * Who may do what on `/admin/users`, written once (§3 of docs/fase-3-admin-owner.md).
 *
 * **It exists so that no screen has a loose `if` about roles in its JSX.** The rule it carries is
 * principle 2 of frontend-diseno.md §1 taken literally: an admin does not find a greyed-out "make
 * this person an admin" button, and does not find one that fails when pressed — there is no button.
 * The owner sees all four roles.
 *
 * **It decides what to show and never what to allow** (#103). The backend authorizes endpoint by
 * endpoint and answers `ROLE_GRANT_FORBIDDEN` or `CANNOT_BLOCK_PRIVILEGED` to anybody who reaches
 * the API another way; this is what keeps the screen from offering an action it knows is refused.
 *
 * Three rules, and they are the whole of §3's matrix for F3.1:
 *
 * 1. An `Owner` moves all four roles; an `Admin` moves `Player` and `Master` and nothing else.
 * 2. **Nobody** blocks an account holding `Admin` or `Owner` — not even an owner. Between peers
 *    there is no authority, and generalising it that far covers self-blocking for free, since the
 *    reader always holds one of the two.
 * 3. `Deleted` is out of reach in both directions: F3.1 neither produces that status nor undoes it.
 *
 * @returns which roles the reader may move, and whether a given account's status is theirs to change
 */
export function useUserAdminCapabilities(): UserAdminCapabilities {
  const { data: me, isPending } = useMe()
  const isOwner = me?.roles.includes('Owner') ?? false
  const isAdmin = me?.roles.includes('Admin') ?? false

  const grantableRoles: readonly PlatformRole[] = isOwner ? PLATFORM_ROLES : isAdmin ? ADMIN_GRANTABLE_ROLES : []

  function canChangeStatus(user: Pick<AdminUserSummary, 'roles' | 'status'>): boolean {
    if (!isOwner && !isAdmin) return false
    if (user.status === 'Deleted') return false
    return !isPrivileged(user.roles)
  }

  return { grantableRoles, canChangeStatus, isPending }
}
