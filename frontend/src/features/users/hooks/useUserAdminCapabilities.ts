import { useMe } from '../api/useMe'
import { ADMIN_GRANTABLE_ROLES, OWNER_GRANTABLE_ROLES, isPrivileged } from '../roles'
import type { AdminUserSummary, PlatformRole } from '../types'

/** What `/admin/users` is allowed to offer the person reading it. */
export interface UserAdminCapabilities {
  /**
   * The roles this reader may hand out and take back, in the order of ADR-07 §8. Never includes
   * `Owner`, which the application does not grant to anybody (ADR-06 §1). Empty for anybody who is
   * neither an admin nor an owner, and empty until the profile arrives.
   */
  grantableRoles: readonly PlatformRole[]
  /**
   * Whether the reader is an `Owner`, who alone may take `Owner` away from somebody who holds it —
   * the one role that can be revoked here without being grantable.
   */
  isOwner: boolean
  /** The reader's own id, so a screen can tell when the account in front of them is theirs. Undefined until the profile arrives. */
  userId: string | undefined
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
 * Who may do what on `/admin/users`, written once (§3 of roles-y-alcance.md).
 *
 * **It exists so that no screen has a loose `if` about roles in its JSX.** The rule it carries is
 * principle 2 of diseno §1 taken literally: an admin does not find a greyed-out "make
 * this person an admin" button, and does not find one that fails when pressed — there is no button.
 * The owner sees the three roles the application grants, and `Owner` only where somebody holds it.
 *
 * **It decides what to show and never what to allow** (ADR-20 §1). The backend authorizes endpoint by
 * endpoint and answers `ROLE_GRANT_FORBIDDEN` or `CANNOT_BLOCK_PRIVILEGED` to anybody who reaches
 * the API another way; this is what keeps the screen from offering an action it knows is refused.
 *
 * Two rules, and they are the whole of §3's matrix for F3.1:
 *
 * 1. An `Owner` grants and revokes `Player`, `Master` and `Admin` and may revoke `Owner`, which
 *    nobody grants (ADR-06 §1); an `Admin` moves `Player` and `Master` and nothing else.
 * 2. **Nobody** blocks an account holding `Admin` or `Owner` — not even an owner. Between peers
 *    there is no authority, and generalising it that far covers self-blocking for free, since the
 *    reader always holds one of the two.
 *
 * @returns which roles the reader may move, and whether a given account's status is theirs to change
 */
export function useUserAdminCapabilities(): UserAdminCapabilities {
  const { data: me, isPending } = useMe()
  const isOwner = me?.roles.includes('Owner') ?? false
  const isAdmin = me?.roles.includes('Admin') ?? false

  const grantableRoles: readonly PlatformRole[] = isOwner ? OWNER_GRANTABLE_ROLES : isAdmin ? ADMIN_GRANTABLE_ROLES : []

  function canChangeStatus(user: Pick<AdminUserSummary, 'roles' | 'status'>): boolean {
    if (!isOwner && !isAdmin) return false
    return !isPrivileged(user.roles)
  }

  return { grantableRoles, isOwner, userId: me?.id, canChangeStatus, isPending }
}
