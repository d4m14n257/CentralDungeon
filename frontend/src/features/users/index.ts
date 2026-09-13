/**
 * Public surface of the users feature (#114): the session's own profile, the picker, and onboarding.
 * Anything not listed here is private to it.
 */

export { ProfileCard } from './components/ProfileCard'
export { UserPicker } from './components/UserPicker'
export { useCompleteOnboarding } from './api/useCompleteOnboarding'
export { useMe } from './api/useMe'
export { useMyProfile } from './api/useMyProfile'
export { useUserProfile } from './api/useUserProfile'
export { useUserSearch } from './api/useUserSearch'
export { userSearchFields } from './searchFields'
export { completeOnboardingSchema, type CompleteOnboardingForm } from './schemas'
/** The feature's domain types. Each is written once in `types.ts` and derived from there (#3.2). */
export type { CompleteOnboardingInput, Profile, User, UserSummary } from './types'
