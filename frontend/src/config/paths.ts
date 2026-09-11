import type { AppContext } from '@/stores/contextStore'

/**
 * Route path patterns for router.tsx registration, plus small builders for links. This is the
 * only place path strings are written (arquitectura.md 3.1.6 regla 2) - E1's subset of the full
 * 28-route sitemap (frontend-diseno.md 2). Future fases add the rest here, not somewhere else.
 *
 * Every context owns a prefix and nothing sits outside one (#222): `/player`, `/master`, `/admin`,
 * plus the entry screens and the two transversal ones. `/` is not a screen - it dispatches to the
 * reader's own home.
 */
export const paths = {
  login: '/login',
  authCallback: '/auth/callback',
  onboarding: '/onboarding',
  root: '/',
  playerHome: 'player',
  playerTableDetail: 'player/tables/:id',
  playerApplications: 'player/applications',
  playerMyTables: 'player/my-tables',
  playerMyTableDetail: 'player/my-tables/:id',
  mySchedule: 'my/schedule',
  myFiles: 'my/files',
  notifications: 'notifications',
  help: 'help',
  helpPlayers: 'players',
  helpMasters: 'masters',
  helpAdmins: 'admins',
  masterDashboard: 'master',
  masterTables: 'master/tables',
  masterTableNew: 'master/tables/new',
  masterTableDetail: 'master/tables/:id',
  masterTableEdit: 'master/tables/:id/edit',
  masterTablePlayers: 'master/tables/:id/players',
  masterTableSchedule: 'master/tables/:id/schedule',
  masterTableSessions: 'master/tables/:id/sessions',
  masterTableTasks: 'master/tables/:id/tasks',
  masterTableFiles: 'master/tables/:id/files',
  masterTableStatus: 'master/tables/:id/status',
  adminTables: 'admin/tables',
  adminCatalogs: 'admin/catalogs',
  adminFiles: 'admin/files',
} as const

/**
 * @param id the table
 * @returns the absolute path to its public detail
 */
export function tableDetailPath(id: string): string {
  return `/player/tables/${id}`
}

/** @returns the absolute path to the explorer - the home of the Player context */
export function playerHomePath(): string {
  return '/player'
}

/** @returns the absolute path to the list of what the reader applied to, and how each one went */
export function playerApplicationsPath(): string {
  return '/player/applications'
}

/** @returns the absolute path to the tables the reader plays at */
export function playerMyTablesPath(): string {
  return '/player/my-tables'
}

/**
 * @returns the absolute path to the master's work tray — the home of the Master context (#136).
 *          It is where the context switcher and the logo land, because it is the screen that says
 *          what to do next
 */
export function masterDashboardPath(): string {
  return '/master'
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
 * @returns the absolute path to the form that rewrites it (#189). A sibling of the detail and not
 *          one of its tabs, the same way the wizard is a sibling of the list: correcting a whole
 *          table is its own screen, not a panel inside another
 */
export function masterTableEditPath(id: string): string {
  return `/master/tables/${id}/edit`
}

/**
 * @param id the table
 * @returns the absolute path to its people tab — who runs it and who plays at it
 */
export function masterTablePlayersPath(id: string): string {
  return `/master/tables/${id}/players`
}

/**
 * @param id the table
 * @returns the absolute path to its agenda tab — the weekly shape, in the reader's time (#22)
 */
export function masterTableSchedulePath(id: string): string {
  return `/master/tables/${id}/schedule`
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
  return `/player/my-tables/${id}`
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

/**
 * Which context a path belongs to, or `null` when it belongs to none.
 *
 * The three contexts each own a prefix - `/player`, `/master`, `/admin` - so the URL alone says
 * which navigation the reader is looking at. That is what lets the header report the context
 * instead of guessing it from a value chosen who knows when (#222).
 *
 * `/notifications`, `/help`, `/my/schedule` and the entry screens deliberately return `null`: they
 * are transversal
 * and belong to whoever is reading them. Flipping the chip to another context on the way to the
 * inbox would be a worse lie than the one this replaces.
 *
 * @param pathname the current location's pathname
 * @returns the context that owns it, or null when no context does
 */
export function contextOfPath(pathname: string): AppContext | null {
  const segment = pathname.split('/')[1]
  if (segment === 'player') return 'player'
  if (segment === 'master') return 'master'
  if (segment === 'admin') return 'admin'
  return null
}

/**
 * Where a context starts - the screen its logo, its switcher entry and the post-login redirect all
 * land on (#222).
 *
 * @param context the context to enter
 * @returns the absolute path to its home
 */
export function homePathFor(context: AppContext): string {
  if (context === 'master') return masterDashboardPath()
  if (context === 'admin') return adminTablesPath()
  return playerHomePath()
}

/**
 * @returns the absolute path to the reader's own week (#227). Under `/my` and not under a context:
 *          the evenings somebody runs and the evenings they play are the same evenings, so the
 *          screen belongs to the person and not to one of their roles
 */
export function mySchedulePath(): string {
  return '/my/schedule'
}

/**
 * @returns the absolute path to the reader's own files
 * @remarks Under `/my` and not under a role, for the same reason as `/my/schedule` (#222): what
 *          somebody uploaded as a player and what they uploaded as a master is one library, and
 *          splitting it would ask them to remember which hat they were wearing
 */
export function myFilesPath(): string {
  return '/my/files'
}
