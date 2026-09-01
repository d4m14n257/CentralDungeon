/**
 * Route path patterns for router.tsx registration, plus small builders for links. This is the
 * only place path strings are written (arquitectura.md 3.1.6 regla 2) - E1's subset of the full
 * 28-route sitemap (frontend-diseno.md 2). Future etapas add the rest here, not somewhere else.
 */
export const paths = {
  login: '/login',
  authCallback: '/auth/callback',
  onboarding: '/onboarding',
  home: '/',
  tableDetail: 'tables/:id',
  myApplications: 'my/applications',
  myTables: 'my/tables',
  notifications: 'notifications',
  masterTables: 'master/tables',
  masterTableNew: 'master/tables/new',
  masterTableDetail: 'master/tables/:id',
  masterTableStatus: 'master/tables/:id/status',
  adminTables: 'admin/tables',
} as const

export function tableDetailPath(id: string): string {
  return `/tables/${id}`
}

export function masterTablesPath(): string {
  return '/master/tables'
}

export function masterTableNewPath(): string {
  return '/master/tables/new'
}

export function masterTableDetailPath(id: string): string {
  return `/master/tables/${id}`
}

export function masterTableStatusPath(id: string): string {
  return `/master/tables/${id}/status`
}

export function adminTablesPath(): string {
  return '/admin/tables'
}
