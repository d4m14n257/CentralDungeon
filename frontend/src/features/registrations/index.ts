/**
 * Public surface of the registrations feature (#114) - applying to a table, and everything a master
 * does about the queue. Anything not listed here is private to it.
 */

export { useAcceptRegistration } from './api/useAcceptRegistration'
export { useApplyToTable } from './api/useApplyToTable'
export { useBlockPlayer } from './api/useBlockPlayer'
export { useCandidates } from './api/useCandidates'
export { useMyApplications } from './api/useMyApplications'
export { useRejectRegistration } from './api/useRejectRegistration'
export { useRequestPlayerBlock } from './api/useRequestPlayerBlock'
export { useTablePlayers } from './api/useTablePlayers'
export { useUnblockPlayer } from './api/useUnblockPlayer'
export { useWithdrawApplication } from './api/useWithdrawApplication'
export { ApplyToTableDialog } from './components/ApplyToTableDialog'
export { BlockPlayerDialog, type VetoAction } from './components/BlockPlayerDialog'
export { RegistrationStatusBadge } from './components/RegistrationStatusBadge'
export { RejectRegistrationDialog } from './components/RejectRegistrationDialog'
export { VETO_ERROR_CODES, vetoErrorKey } from './vetoErrors'
export { blockRegistrationSchema, type BlockRegistrationForm } from './schemas'
/** The feature's domain types. Each is written once in `types.ts` and derived from there (#3.2). */
export type { CreateRegistrationInput, Registration, RegistrationFile, RegistrationStatus, TablePlayer } from './types'
