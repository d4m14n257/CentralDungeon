/**
 * Public surface of the tables feature (#114) - the aggregate the whole app revolves around.
 * Anything not listed here is private to it.
 *
 * This is the only kind of barrel the project has: one per feature, at its boundary. An `index.ts`
 * per folder would reintroduce import cycles and draw no boundary at all (#3.1.3).
 */

export { CreateUnassignedTableDialog } from './components/CreateUnassignedTableDialog'
export { GameTableCard } from './components/GameTableCard'
export { JustifiedTableActionDialog } from './components/JustifiedTableActionDialog'
export { TableStatusBadge } from './components/TableStatusBadge'
export { useAdminTables } from './api/useAdminTables'
export { useApproveTable } from './api/useApproveTable'
export { useAssignMasters } from './api/useAssignMasters'
export { useCancelTable } from './api/useCancelTable'
export { useCreateTable } from './api/useCreateTable'
export { useDeleteTable } from './api/useDeleteTable'
export { useCreateUnassignedTable } from './api/useCreateUnassignedTable'
export { useFinishTable } from './api/useFinishTable'
export { useGameTable } from './api/useGameTable'
export { useGameTables } from './api/useGameTables'
export { useManagedTable } from './api/useManagedTable'
export { useManagedTables } from './api/useManagedTables'
export { useMyTables } from './api/useMyTables'
export { useRequestChanges } from './api/useRequestChanges'
export { useResubmitTable } from './api/useResubmitTable'
export { useStartTable } from './api/useStartTable'
export { useTableStatusHistory } from './api/useTableStatusHistory'
export { changeTableStatusSchema, createGameTableSchema } from './schemas'
/** The form value types, inferred from the zod schemas above. */
export type { ChangeTableStatusForm, CreateGameTableForm } from './schemas'
/** The feature's domain types. Each is written once in `types.ts` and derived from there (#3.2). */
export type {
  AdminTableSummary,
  AssignMastersRequest,
  ChangeTableStatusRequest,
  CreateGameTableRequest,
  GameTableDetail,
  GameTableStatus,
  GameTableSummary,
  MasterSummary,
  TableStatusChange,
} from './types'
