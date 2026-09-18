/**
 * Public surface of the approvals feature (#114): the one mechanism behind every request somebody
 * makes of an admin (#42), from the three screens that raise one to the tray that resolves them.
 * Anything not listed here is private to it.
 */

export { RequestDetailPanel } from './components/RequestDetailPanel'
export { RequestStatusBadge } from './components/RequestStatusBadge'
export { RequestTypeBadge } from './components/RequestTypeBadge'
export { ResolveRequestDialog, type ResolveAction } from './components/ResolveRequestDialog'
export { SubmitRequestDialog } from './components/SubmitRequestDialog'
/** The three screens that prompt a request use this and nothing else of the feature. */
export { SubmitRequestSection } from './components/SubmitRequestSection'
export { useAdminRequest } from './api/useAdminRequest'
export { useAdminRequests } from './api/useAdminRequests'
export { useApproveRequest } from './api/useApproveRequest'
export { useMyRequests } from './api/useMyRequests'
export { useRejectRequest } from './api/useRejectRequest'
export { useSubmitRequest } from './api/useSubmitRequest'
export { approvalRequestSearchFields, requestStatusChoices, requestTypeChoices } from './searchFields'
export { APPROVAL_REQUEST_TYPES, APPROVAL_STATUSES, PENDING_REQUESTS_QUERY } from './requestTypes'
export { APPROVAL_ERROR_CODES, approvalErrorKey } from './approvalErrors'
export {
  resolveApprovalRequestSchema,
  submitApprovalRequestSchema,
  type ResolveApprovalRequestForm,
  type SubmitApprovalRequestForm,
} from './schemas'
/** The feature's domain types. Each is written once in `types.ts` and derived from there (§3.2). */
export type {
  ApprovalRequestDetail,
  ApprovalRequestSummary,
  ApprovalRequestType,
  ApprovalStatus,
  ResolveApprovalRequestInput,
  SubmitApprovalRequestInput,
} from './types'
