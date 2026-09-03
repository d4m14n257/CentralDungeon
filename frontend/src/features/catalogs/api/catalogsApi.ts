import { api } from '@/api/client'
import { pageSize } from '@/config/pagination'

import type {
  AcceptCatalogValueInput,
  AdminCatalogValue,
  CatalogKind,
  CatalogStatus,
  CatalogValue,
  DisableCatalogValueInput,
  MergeCatalogGroupsInput,
  ProposeCatalogValueInput,
  SplitCatalogGroupInput,
} from '../types'

/**
 * Every call about the three catalogs, split the way the backend splits them: what anyone may do
 * lives under `/api/v1/{kind}`, and the six admin operations under `/api/v1/admin/catalogs/{kind}`.
 *
 * The kind is a parameter rather than three copies of each function, for the same reason it is a
 * path variable on the server: systems, tags and platforms are the same shape, and writing this out
 * three times would mean fixing every bug three times.
 */
export const catalogsApi = {
  /**
   * Accepted values only - the backend never returns anything else here (#57).
   *
   * @param kind  which catalog
   * @param query the search box, or undefined for the whole catalog
   * @param size  how many to bring; the combobox wants a short list, not a page of a table
   */
  list: (kind: CatalogKind, query?: string, size = pageSize.picker) => api.getPage<CatalogValue>(`/api/v1/${kind}`, { q: query, size }),

  /**
   * One value whatever its status, so a pending proposal can be shown as pending (#57).
   *
   * @param kind which catalog
   * @param id   the value to read
   */
  byId: (kind: CatalogKind, id: string) => api.get<CatalogValue>(`/api/v1/${kind}/${id}`),

  /**
   * Proposes a value. Masters and admins may; it is born `Created` and shows to nobody until an
   * admin accepts it (#55, #57).
   *
   * @param kind  which catalog
   * @param input the name to add
   */
  propose: (kind: CatalogKind, input: ProposeCatalogValueInput) =>
    api.post<CatalogValue, ProposeCatalogValueInput>(`/api/v1/${kind}`, input),

  /**
   * /admin/catalogs: everything, whatever its status, with each value's group and usage count.
   *
   * @param kind     which catalog
   * @param query    the search box, or undefined for everything
   * @param statuses the statuses to keep, or undefined for no filter - which is the default,
   *                 because reviewing what was proposed is the point of the screen
   * @param page     zero-based page number
   */
  adminList: (kind: CatalogKind, query?: string, statuses?: CatalogStatus[], page = 0) =>
    api.getPage<AdminCatalogValue>(`/api/v1/admin/catalogs/${kind}`, {
      q: query,
      status: statuses?.join(','),
      page,
      size: pageSize.adminQueue,
    }),

  /**
   * One value's whole synonym group, canonical entry first, every member whatever its status.
   *
   * Not paginated - depth is always 1 (#59), so a group is bounded by how many synonyms a community
   * writes for one thing, and it is read as a whole.
   *
   * @param kind which catalog
   * @param id   any member of the group
   */
  group: (kind: CatalogKind, id: string) => api.get<AdminCatalogValue[]>(`/api/v1/admin/catalogs/${kind}/${id}/group`),

  /**
   * Accepts a proposal and classifies it in one step (#55).
   *
   * @param kind  which catalog
   * @param id    the proposal
   * @param input the group to join, or a null `canonicalId` for a group of its own
   */
  accept: (kind: CatalogKind, id: string, input: AcceptCatalogValueInput) =>
    api.post<AdminCatalogValue, AcceptCatalogValueInput>(`/api/v1/admin/catalogs/${kind}/${id}/accept`, input),

  /**
   * Turns down a proposal: it never shows and never filters (#57).
   *
   * @param kind which catalog
   * @param id   the proposal
   */
  reject: (kind: CatalogKind, id: string) => api.post<AdminCatalogValue>(`/api/v1/admin/catalogs/${kind}/${id}/reject`),

  /**
   * Merges two synonym groups. Answers with the surviving canonical entry, which is the row the
   * screen has to refresh - the source stops being one.
   *
   * @param kind  which catalog
   * @param input the group that stops being one, and the one that survives
   */
  merge: (kind: CatalogKind, input: MergeCatalogGroupsInput) =>
    api.post<AdminCatalogValue, MergeCatalogGroupsInput>(`/api/v1/admin/catalogs/${kind}/merge`, input),

  /**
   * Takes one alias out of its group; it becomes a canonical entry of its own.
   *
   * @param kind  which catalog
   * @param input the alias that leaves
   */
  split: (kind: CatalogKind, input: SplitCatalogGroupInput) =>
    api.post<AdminCatalogValue, SplitCatalogGroupInput>(`/api/v1/admin/catalogs/${kind}/split`, input),

  /**
   * Takes a value out of circulation without breaking a single link (#81).
   *
   * @param kind  which catalog
   * @param id    the value
   * @param input the successor, needed only when the value is a canonical entry with live aliases
   */
  disable: (kind: CatalogKind, id: string, input: DisableCatalogValueInput) =>
    api.post<AdminCatalogValue, DisableCatalogValueInput>(`/api/v1/admin/catalogs/${kind}/${id}/disable`, input),

  /**
   * Puts a disabled value back in circulation, in the group it was in (#81).
   *
   * @param kind which catalog
   * @param id   the value
   */
  restore: (kind: CatalogKind, id: string) => api.post<AdminCatalogValue>(`/api/v1/admin/catalogs/${kind}/${id}/restore`),
}
