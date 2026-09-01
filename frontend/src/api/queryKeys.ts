/** Central query key factory (arquitectura.md 3.3) - never a loose string literal in a component. */
export const queryKeys = {
  tables: {
    list: (filters?: Record<string, unknown>) => ['tables', 'list', filters] as const,
    detail: (id: string) => ['tables', 'detail', id] as const,
    managedDetail: (id: string) => ['tables', 'managed-detail', id] as const,
    mine: () => ['tables', 'mine'] as const,
    managed: () => ['tables', 'managed'] as const,
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
  },
  system: {
    health: () => ['system', 'health'] as const,
  },
} as const
