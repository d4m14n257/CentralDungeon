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
  sessions: {
    /** A table's calendar, as the people running it see it. The whole list, never paginated. */
    list: (tableId: string) => ['sessions', 'list', tableId] as const,
    /**
     * The player's own calendar and attendance. Its own branch rather than a filter over `list`:
     * they are two different answers from the server, and whoever reads one can rarely read the other.
     */
    mine: (tableId: string) => ['sessions', 'mine', tableId] as const,
  },
  files: {
    /** The reuse history of #65, keyed by what the picker searched. */
    mine: (query?: string, page = 0) => ['files', 'mine', query, page] as const,
    /** What the platform published, by audience (#64). */
    public: (audience?: string) => ['files', 'public', audience] as const,
    /**
     * One table's attachments, as the people running it see them. Its own branch and not part of
     * `tables.detail`: what a master sees includes the private ones, and the table's detail carries
     * only what it shares - two different answers that must not share a cache entry (#79).
     */
    table: (tableId: string) => ['files', 'table', tableId] as const,
    /** The /admin/files table. Every admin mutation invalidates this branch and nothing else. */
    admin: (query?: string, statuses?: string[], fileTypes?: string[], page = 0) =>
      ['files', 'admin', query, statuses, fileTypes, page] as const,
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
