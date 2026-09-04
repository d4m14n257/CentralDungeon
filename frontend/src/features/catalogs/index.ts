/**
 * Public surface of the catalogs feature (#114): systems, tags and platforms, the synonym groups
 * that hold them together, and the six operations that make those groups an admin's job.
 *
 * Anything not listed here is private to the feature - from outside, the import is always
 * `@/features/catalogs`, never a path inside it.
 */

export { AcceptCatalogValueDialog } from './components/AcceptCatalogValueDialog'
export { CanonicalPicker } from './components/CanonicalPicker'
export { CatalogChip } from './components/CatalogChip'
export { CatalogCombobox } from './components/CatalogCombobox'
export { CatalogPicker } from './components/CatalogPicker'
export { CatalogStatusBadge } from './components/CatalogStatusBadge'
export { DisableCatalogValueDialog } from './components/DisableCatalogValueDialog'
export { MergeCatalogGroupsDialog } from './components/MergeCatalogGroupsDialog'

export { useAcceptCatalogValue } from './api/useAcceptCatalogValue'
export { useAdminCatalog } from './api/useAdminCatalog'
export { useCatalogGroup } from './api/useCatalogGroup'
export { useCatalogValue } from './api/useCatalogValue'
export { useCatalogValues } from './api/useCatalogValues'
export { useDisableCatalogValue } from './api/useDisableCatalogValue'
export { useMergeCatalogGroups } from './api/useMergeCatalogGroups'
export { useProposeCatalogValue } from './api/useProposeCatalogValue'
export { useRejectCatalogValue } from './api/useRejectCatalogValue'
export { useRestoreCatalogValue } from './api/useRestoreCatalogValue'
export { useSplitCatalogGroup } from './api/useSplitCatalogGroup'

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
  SplitCatalogGroupInput,
} from './types'
