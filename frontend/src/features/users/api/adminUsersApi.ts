import { api } from '@/api/client'
import { pageSize } from '@/config/pagination'

import type { AdminUserDetail, AdminUserSummary, ChangeUserRoleInput, ChangeUserStatusInput, UserAdminChange } from '../types'

/**
 * The seven calls of `/admin/users` (F3.1). A module of its own next to {@link usersApi} and not a
 * handful of extra functions inside it: these all answer to `hasAnyRole('ADMIN','OWNER')` and see
 * accounts nothing else in the application may see, the same way `catalogsApi` keeps its six admin
 * operations under their own prefix.
 *
 * **The four mutators answer with the updated detail** rather than `204`, which is what lets a
 * caller confirm the outcome — a grant of `Admin` comes back without `Owner` on it (#169), so the
 * answer is the only place the second half of the transaction is visible. The hooks on top of it
 * still re-read the branch instead of patching one row in: one of these calls moves roles this
 * screen never asked about, and the listing has no way to know which rows those were.
 *
 * **The actor is never a parameter.** Who is acting comes from the token, and an id in the body
 * would be a claim the caller makes about themselves (arquitectura.md §2.6).
 *
 * The two role sub-routes are `grant-role` and `revoke-role`, flat, and not `roles/grant`: that is
 * what `AdminUserController` registers, and it is the style `GameTableController` already uses for
 * its own actions. Where the contract and the code disagree, the code wins.
 */
export const adminUsersApi = {
  /**
   * The `/admin/users` table: every account, blocked ones included.
   *
   * @param query the search box, already debounced, in the language of `lib/searchQuery.ts`
   * @param page  zero-based page number
   */
  list: (query: string | undefined, page = 0) =>
    api.getPage<AdminUserSummary>('/api/v1/admin/users', { q: query, page, size: pageSize.adminQueue }),

  /**
   * One account, in full.
   *
   * @param id the account to read
   */
  byId: (id: string) => api.get<AdminUserDetail>(`/api/v1/admin/users/${id}`),

  /**
   * What admins did to this account, and why (#84, #169). Never paginated: it is read as a whole,
   * inside the panel that opens on one person.
   *
   * @param id the account whose history to read
   */
  history: (id: string) => api.get<UserAdminChange[]>(`/api/v1/admin/users/${id}/history`),

  /**
   * Gives somebody a role. A `POST` with a body and not a `PUT`, because the justification is part
   * of the act: a grant nobody explained is a grant nobody can review (#169).
   *
   * Refused with `ROLE_GRANT_FORBIDDEN` when an admin reaches for `Admin` or `Owner` — which this
   * screen never offers them, so it only surfaces for somebody calling the API another way.
   *
   * @param id    the account to promote
   * @param input the role and the reason
   */
  grantRole: (id: string, input: ChangeUserRoleInput) =>
    api.post<AdminUserDetail, ChangeUserRoleInput>(`/api/v1/admin/users/${id}/grant-role`, input),

  /**
   * Takes a role away. Refused with `CANNOT_REVOKE_OWN_OWNER` or `LAST_OWNER` when it would leave
   * the platform with nobody who can hand the rank back.
   *
   * @param id    the account to demote
   * @param input the role and the reason
   */
  revokeRole: (id: string, input: ChangeUserRoleInput) =>
    api.post<AdminUserDetail, ChangeUserRoleInput>(`/api/v1/admin/users/${id}/revoke-role`, input),

  /**
   * Closes an account (#84). The person stops being able to sign in; **nothing of theirs is
   * deleted** — their tables, applications and history stay exactly where they were.
   *
   * @param id    the account to block
   * @param input the reason
   */
  block: (id: string, input: ChangeUserStatusInput) =>
    api.post<AdminUserDetail, ChangeUserStatusInput>(`/api/v1/admin/users/${id}/block`, input),

  /**
   * Reopens a blocked account.
   *
   * @param id    the account to unblock
   * @param input the reason
   */
  unblock: (id: string, input: ChangeUserStatusInput) =>
    api.post<AdminUserDetail, ChangeUserStatusInput>(`/api/v1/admin/users/${id}/unblock`, input),
}
