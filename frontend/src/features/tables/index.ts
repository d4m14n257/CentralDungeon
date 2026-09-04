/**
 * Public surface of the tables feature (#114) - the aggregate the whole app revolves around.
 * Anything not listed here is private to it.
 *
 * This is the only kind of barrel the project has: one per feature, at its boundary. An `index.ts`
 * per folder would reintroduce import cycles and draw no boundary at all (#3.1.3).
 */

export { AttendanceEditor } from './components/AttendanceEditor'
export { AttendanceSummaryView } from './components/AttendanceSummaryView'
export { CreateUnassignedTableDialog } from './components/CreateUnassignedTableDialog'
export { GameTableCard } from './components/GameTableCard'
export { MasterWorkItemList } from './components/MasterWorkItemList'
export { ScheduleEditor } from './components/ScheduleEditor'
export { JustifiedTableActionDialog } from './components/JustifiedTableActionDialog'
export { SessionList } from './components/SessionList'
export { SessionStatusBadge } from './components/SessionStatusBadge'
export { TableStatusBadge } from './components/TableStatusBadge'
export { useAddMaster } from './api/useAddMaster'
export { useAdminTables } from './api/useAdminTables'
export { useApproveTable } from './api/useApproveTable'
export { useAssignMasters } from './api/useAssignMasters'
export { useCancelSession } from './api/useCancelSession'
export { useCancelTable } from './api/useCancelTable'
export { useCreateTable } from './api/useCreateTable'
export { useDeleteTable } from './api/useDeleteTable'
export { useCreateUnassignedTable } from './api/useCreateUnassignedTable'
export { useFinishTable } from './api/useFinishTable'
export { useGameTable } from './api/useGameTable'
export { useHoldSession } from './api/useHoldSession'
export { useGameTables } from './api/useGameTables'
export { useManagedTable } from './api/useManagedTable'
export { useManagedTables } from './api/useManagedTables'
export { useMySessions } from './api/useMySessions'
export { useMasterDashboard } from './api/useMasterDashboard'
export { useMyTables } from './api/useMyTables'
export { useRecordAttendance } from './api/useRecordAttendance'
export { useRemoveMaster } from './api/useRemoveMaster'
export { useRequestChanges } from './api/useRequestChanges'
export { useResubmitTable } from './api/useResubmitTable'
export { useStartTable } from './api/useStartTable'
export { useTableSessions } from './api/useTableSessions'
export { useTableStatusHistory } from './api/useTableStatusHistory'
export { useTableTypes } from './api/useTableTypes'
export { useUpdateSession } from './api/useUpdateSession'
export { useUpdateTable } from './api/useUpdateTable'
export { changeTableStatusSchema, createGameTableSchema, WIZARD_STEPS } from './schemas'
/** The form value types, inferred from the zod schemas above, plus the wizard's four steps. */
export type { ChangeTableStatusForm, CreateGameTableForm, WizardStep } from './schemas'
/** The feature's domain types. Each is written once in `types.ts` and derived from there (#3.2). */
export type { SessionListItem } from './components/SessionList'
export type {
  AddMasterRequest,
  AdminTableSummary,
  AttendanceEntryRequest,
  AttendanceStatus,
  AttendanceSummary,
  AssignMastersRequest,
  ChangeTableStatusRequest,
  CreateGameTableRequest,
  GameTableDetail,
  GameTableStatus,
  GameTableSummary,
  MasterDashboard,
  MasterSummary,
  MasterWorkItem,
  MasterWorkItemKind,
  MySessions,
  PlayerSession,
  PublicSession,
  RecordAttendanceRequest,
  SessionAttendanceEntry,
  TableScheduleEntry,
  TableSession,
  TableSessionStatus,
  TableStatusChange,
  TableType,
  UpdateGameTableRequest,
  UpdateSessionRequest,
} from './types'
