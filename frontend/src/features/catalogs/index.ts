/**
 * Public surface of the catalogs feature (#114): systems, tags and platforms, the synonym groups
 * that hold them together, and the eight operations that make those groups an admin's job, and the canvas they are done on (#275).
 *
 * Anything not listed here is private to the feature - from outside, the import is always
 * `@/features/catalogs`, never a path inside it.
 */

export { CanonicalPicker } from './components/CanonicalPicker'
export { CatalogChip } from './components/CatalogChip'
export { CatalogCombobox } from './components/CatalogCombobox'
export { CatalogGraph } from './components/CatalogGraph'
export { CatalogPicker } from './components/CatalogPicker'
export { CatalogStatusBadge } from './components/CatalogStatusBadge'
export { CreateCatalogValueDialog } from './components/CreateCatalogValueDialog'

export { useAcceptCatalogValue } from './api/useAcceptCatalogValue'
export { useAdminCatalog } from './api/useAdminCatalog'
export { useCatalogGroup } from './api/useCatalogGroup'
export { useCatalogValue } from './api/useCatalogValue'
export { useCatalogValues } from './api/useCatalogValues'
export { useCreateCatalogValue } from './api/useCreateCatalogValue'
export { useDisableCatalogValue } from './api/useDisableCatalogValue'
export { useMergeCatalogGroups } from './api/useMergeCatalogGroups'
export { useProposeCatalogValue } from './api/useProposeCatalogValue'
export { useRejectCatalogValue } from './api/useRejectCatalogValue'
export { useRestoreCatalogValue } from './api/useRestoreCatalogValue'

/** The feature's domain types. Each is written once in `types.ts` and derived from there (#3.2). */
export type {
  AcceptCatalogValueInput,
  AdminCatalogValue,
  CatalogKind,
  CatalogStatus,
  CatalogValue,
  DisableCatalogValueInput,
  MergeCatalogGroupsInput,
  ProposeCatalogValueInput,
  ReassignCatalogValueInput,
  SplitCatalogGroupInput,
} from './types'
