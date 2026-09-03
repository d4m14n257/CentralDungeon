/**
 * Public surface of the users feature (#114): the session's own profile, the picker, and onboarding.
 * Anything not listed here is private to it.
 */

export { UserPicker } from './components/UserPicker'
export { useCompleteOnboarding } from './api/useCompleteOnboarding'
export { useMe } from './api/useMe'
export { useUserSearch } from './api/useUserSearch'
export { completeOnboardingSchema, type CompleteOnboardingForm } from './schemas'
/** The feature's domain types. Each is written once in `types.ts` and derived from there (#3.2). */
export type { CompleteOnboardingInput, User, UserSummary } from './types'
