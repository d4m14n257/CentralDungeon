/** Central query key factory (arquitectura.md 3.3) - never a loose string literal in a component. */
export const queryKeys = {
  tables: {
    list: (filters?: Record<string, unknown>) => ['tables', 'list', filters] as const,
    detail: (id: string) => ['tables', 'detail', id] as const,
    managedDetail: (id: string) => ['tables', 'managed-detail', id] as const,
    mine: () => ['tables', 'mine'] as const,
    managed: () => ['tables', 'managed'] as const,
    admin: (statuses?: string[], page = 0) => ['tables', 'admin', statuses, page] as const,
    statusHistory: (id: string) => ['tables', 'status-history', id] as const,
    /** The platform's table types. One list for the whole app - admins change it rarely (#72). */
    types: () => ['tables', 'types'] as const,
  },
  catalogs: {
    /** The whole branch. What every catalog mutation invalidates: one admin action can move rows
     *  that are not on screen - a merge repoints a group, a disable hands it to a successor - so
     *  patching one entry would leave the rest of the cache describing a state that no longer is. */
    all: () => ['catalogs'] as const,
    /** The accepted values a combobox offers. Keyed by catalog and by what was typed. */
    list: (kind: string, query?: string) => ['catalogs', 'list', kind, query] as const,
    /** One value by id, whatever its status - how a pending proposal is read back (#57). */
    detail: (kind: string, id: string) => ['catalogs', 'detail', kind, id] as const,
    /** One value's whole synonym group - what the merge and disable dialogs are built on. */
    group: (kind: string, id: string) => ['catalogs', 'group', kind, id] as const,
    /** The /admin/catalogs table. Every admin mutation invalidates this branch and nothing else. */
    admin: (kind: string, query?: string, statuses?: string[], page = 0) => ['catalogs', 'admin', kind, query, statuses, page] as const,
  },
  registrations: {
    candidates: (tableId: string) => ['registrations', 'candidates', tableId] as const,
    mine: () => ['registrations', 'mine'] as const,
  },
  notifications: {
    list: () => ['notifications', 'list'] as const,
  },
  users: {
    me: () => ['users', 'me'] as const,
    search: (query: string) => ['users', 'search', query] as const,
  },
  system: {
    health: () => ['system', 'health'] as const,
  },
} as const
