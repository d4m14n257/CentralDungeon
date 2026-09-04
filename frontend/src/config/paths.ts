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
  myTableDetail: 'my/tables/:id',
  notifications: 'notifications',
  help: 'help',
  helpPlayers: 'players',
  helpMasters: 'masters',
  helpAdmins: 'admins',
  masterTables: 'master/tables',
  masterTableNew: 'master/tables/new',
  masterTableDetail: 'master/tables/:id',
  masterTableSessions: 'master/tables/:id/sessions',
  masterTableTasks: 'master/tables/:id/tasks',
  masterTableFiles: 'master/tables/:id/files',
  masterTableStatus: 'master/tables/:id/status',
  adminTables: 'admin/tables',
  adminCatalogs: 'admin/catalogs',
  adminFiles: 'admin/files',
} as const

/** The help's audiences (#168). The index, `/help`, is the one that serves everybody. */
export type HelpAudience = 'players' | 'masters' | 'admins'

/**
 * `helpPath()` -> /help, `helpPath('admins', 'assign-masters')` -> /help/admins#assign-masters.
 * Always absolute: the patterns above are relative because the router consumes them, and a
 * `<Link to={paths.help}>` from /admin/tables resolved to /admin/tables/help.
 */
export function helpPath(audience?: HelpAudience, ref?: string): string {
  return `/help${audience ? `/${audience}` : ''}${ref ? `#${ref}` : ''}`
}

/**
 * @param id the table
 * @returns the absolute path to its public detail
 */
export function tableDetailPath(id: string): string {
  return `/tables/${id}`
}

/** @returns the absolute path to the master's table list */
export function masterTablesPath(): string {
  return '/master/tables'
}

/** @returns the absolute path to the create-table wizard */
export function masterTableNewPath(): string {
  return '/master/tables/new'
}

/**
 * @param id the table
 * @returns the absolute path to the master's view of it
 */
export function masterTableDetailPath(id: string): string {
  return `/master/tables/${id}`
}

/**
 * @param id the table
 * @returns the absolute path to its sessions tab
 */
export function masterTableSessionsPath(id: string): string {
  return `/master/tables/${id}/sessions`
}

/**
 * @param id the table
 * @returns the absolute path to its tasks tab — what the table asks of its people (#63)
 */
export function masterTableTasksPath(id: string): string {
  return `/master/tables/${id}/tasks`
}

/**
 * @param id the table
 * @returns the absolute path to its files tab
 */
export function masterTableFilesPath(id: string): string {
  return `/master/tables/${id}/files`
}

/**
 * @param id the table
 * @returns the absolute path to its status tab
 */
export function masterTableStatusPath(id: string): string {
  return `/master/tables/${id}/status`
}

/**
 * @param id the table
 * @returns the absolute path to the player's own view of it - agenda, sessions and their attendance
 */
export function myTableDetailPath(id: string): string {
  return `/my/tables/${id}`
}

/** @returns the absolute path to the admin's table list */
export function adminTablesPath(): string {
  return '/admin/tables'
}

/** @returns the absolute path to the catalog administration screen */
export function adminCatalogsPath(): string {
  return '/admin/catalogs'
}

/** @returns the absolute path to the file administration screen (#64, #79) */
export function adminFilesPath(): string {
  return '/admin/files'
}
