/**
 * Route path patterns for router.tsx registration, plus small builders for links. This is the
 * only place path strings are written (arquitectura.md 3.1.6 regla 2) - E1's subset of the full
 * 28-route sitemap (frontend-diseno.md 2). Future fases add the rest here, not somewhere else.
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
  help: 'help',
  helpPlayers: 'players',
  helpMasters: 'masters',
  helpAdmins: 'admins',
  masterTables: 'master/tables',
  masterTableNew: 'master/tables/new',
  masterTableDetail: 'master/tables/:id',
  masterTableStatus: 'master/tables/:id/status',
  adminTables: 'admin/tables',
} as const

/** Las audiencias de la ayuda (#168). El índice, `/help`, es la que sirve a todos. */
export type HelpAudience = 'players' | 'masters' | 'admins'

/**
 * `helpPath()` -> /help, `helpPath('admins', 'assign-masters')` -> /help/admins#assign-masters.
 * Siempre absoluto: los patrones de arriba son relativos porque los consume el router, y un
 * `<Link to={paths.help}>` desde /admin/tables resolvía a /admin/tables/help.
 */
export function helpPath(audience?: HelpAudience, ref?: string): string {
  return `/help${audience ? `/${audience}` : ''}${ref ? `#${ref}` : ''}`
}

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
