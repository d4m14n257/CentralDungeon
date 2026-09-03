/**
 * Public surface of the registrations feature (#114) - applying to a table, and everything a master
 * does about the queue. Anything not listed here is private to it.
 */

export { useAcceptRegistration } from './api/useAcceptRegistration'
export { useApplyToTable } from './api/useApplyToTable'
export { useCandidates } from './api/useCandidates'
export { useMyApplications } from './api/useMyApplications'
export { useRejectRegistration } from './api/useRejectRegistration'
export { useWithdrawApplication } from './api/useWithdrawApplication'
export { ApplyToTableDialog } from './components/ApplyToTableDialog'
export { RegistrationStatusBadge } from './components/RegistrationStatusBadge'
export { RejectRegistrationDialog } from './components/RejectRegistrationDialog'
/** The feature's domain types. Each is written once in `types.ts` and derived from there (#3.2). */
export type { CreateRegistrationInput, Registration, RegistrationStatus } from './types'
