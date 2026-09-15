/**
 * Public surface of the users feature (#114): the session's own profile, the picker, onboarding, and
 * the administration of accounts that `/admin/users` is built on (F3.1).
 * Anything not listed here is private to it.
 */

export { AdminUserRolesCell } from './components/AdminUserRolesCell'
export { BlockUserDialog } from './components/BlockUserDialog'
export { ProfileCard } from './components/ProfileCard'
export { RoleChangeDialog } from './components/RoleChangeDialog'
export { UserAdminHistory } from './components/UserAdminHistory'
export { UserPicker } from './components/UserPicker'
export { UserStatusBadge } from './components/UserStatusBadge'
export { useAdminUser } from './api/useAdminUser'
export { useAdminUserHistory } from './api/useAdminUserHistory'
export { useAdminUsers } from './api/useAdminUsers'
export { useBlockUser } from './api/useBlockUser'
export { useCompleteOnboarding } from './api/useCompleteOnboarding'
export { useGrantRole } from './api/useGrantRole'
export { useMe } from './api/useMe'
export { useMyProfile } from './api/useMyProfile'
export { useRevokeRole } from './api/useRevokeRole'
export { useUnblockUser } from './api/useUnblockUser'
export { useUserProfile } from './api/useUserProfile'
export { useUserSearch } from './api/useUserSearch'
/** Who may do what on /admin/users - the rule of §3, so no screen holds a loose `if` about roles. */
export { useUserAdminCapabilities, type UserAdminCapabilities } from './hooks/useUserAdminCapabilities'
export { adminUserSearchFields, userSearchFields } from './searchFields'
export { ACCOUNT_STATUSES, PLATFORM_ROLES, isPrivileged } from './roles'
export { USER_ADMIN_ERROR_CODES, userAdminErrorKey } from './adminErrors'
export {
  changeUserRoleSchema,
  changeUserStatusSchema,
  completeOnboardingSchema,
  type ChangeUserRoleForm,
  type ChangeUserStatusForm,
  type CompleteOnboardingForm,
} from './schemas'
/** The feature's domain types. Each is written once in `types.ts` and derived from there (#3.2). */
export type {
  AccountStatus,
  AdminUserDetail,
  AdminUserSummary,
  ChangeUserRoleInput,
  ChangeUserStatusInput,
  CompleteOnboardingInput,
  PlatformRole,
  Profile,
  User,
  UserAdminChange,
  UserAdminChangeType,
  UserSummary,
} from './types'
