import type { CatalogValue } from '@/types/catalog'
import type { Weekday } from '@/lib/date'

/**
 * A table's nine lifecycle states plus `Deleted`. A union of literals, not a TS `enum`: the backend
 * serializes them as strings, and the union is what makes `Record<GameTableStatus, …>` force every
 * case to be covered when mapping to labels or badge variants (#3.2 regla 9).
 */
export type GameTableStatus =
  'Unassigned' | 'Preparation' | 'ChangesRequested' | 'Opened' | 'InProgress' | 'PauseRequested' | 'Pause' | 'Finished' | 'Canceled'

/**
 * One of a table's masters, as it arrives nested in a table response. `masterType` is `Primary` or
 * `Secondary` on the wire; on screen these read as "master" and "co-master" and never as these
 * words (#166).
 */
export interface MasterSummary {
  userId: string
  name: string
  karma: number
  masterType: 'Primary' | 'Secondary'
}

/**
 * One slot of a table's weekly agenda, exactly as `TableScheduleEntry` sends it: **UTC on the wire**
 * (#22). What the screen shows is the reader's own time, converted with `lib/date.ts` at the edge —
 * a component that receives one of these has not converted it yet.
 */
export interface TableScheduleEntry {
  weekday: Weekday
  /** `HH:mm:ss` as the API writes it; the seconds are always zero and always ignored. */
  hourtime: string
}

/** Espejo de GameTableSummaryResponse. */
export interface GameTableSummary {
  id: string
  name: string
  status: GameTableStatus
  tableTypeName: string | null
  maxPlayers: number | null
  playerCount: number
  /** `HH:mm:ss` - how long one session lasts, so a card can close the range and not only open it. */
  duration: string | null
  schedule: TableScheduleEntry[]
  /**
   * Whether this table overlaps something the reader is already committed to (#178). Computed by
   * the server for the actor of the token and for nobody else (#121) - the card only shows it.
   */
  scheduleConflict: boolean
  primaryMaster: MasterSummary
}

/**
 * Espejo de CreateGameTableRequest - forma de entrada, no se deriva de GameTableDetail: la mesa
 * entra por id de tipo y de catálogo, y sale por nombre y por valor completo.
 */
export interface CreateGameTableRequest {
  name: string
  description?: string | null
  /** The house rules - what is allowed at this table. Rich text (#62). */
  permitted?: string | null
  requirements?: string | null
  tableTypeId?: string | null
  systemIds?: string[] | null
  tagIds?: string[] | null
  platformIds?: string[] | null
  startDate?: string | null
  duration?: string | null
  totalSessions?: number | null
  maxPlayers?: number | null
  /** The weekly agenda, already converted to UTC by `lib/date.ts` before it is sent (#22). */
  schedule?: TableScheduleEntry[] | null
}

/**
 * Espejo de UpdateGameTableRequest. Identical in shape to the create request today, and kept as its
 * own name rather than an alias because the two are different messages: the day one of them grows a
 * field the other should not have, the type is already there to grow it in.
 */
export type UpdateGameTableRequest = CreateGameTableRequest

/** Espejo de GameTableDetailResponse. */
export interface GameTableDetail {
  id: string
  name: string
  description: string | null
  permitted: string | null
  requirements: string | null
  tableTypeName: string | null
  status: GameTableStatus
  maxPlayers: number | null
  playerCount: number
  startDate: string | null
  duration: string | null
  totalSessions: number | null
  /** The weekly agenda, in UTC (#22). Ordered as a week reads. */
  schedule: TableScheduleEntry[]
  /**
   * The materialized calendar (#26, #33): dates, not the weekly shape. It travels inside the detail
   * rather than on an endpoint of its own because this read already decides who may see the table.
   * Empty until the table opens, and while it is paused it carries only what already happened (#32).
   */
  sessions: PublicSession[]
  /** Each value under the alias its master chose, never rewritten to the group's canonical one (#58). */
  systems: CatalogValue[]
  tags: CatalogValue[]
  platforms: CatalogValue[]
  masters: MasterSummary[]
  createdAt: string
  /** When it entered Finished or Canceled (#180), or null while it is still going. */
  closedAt: string | null
  /**
   * Whether this table clashes with something the reader is already committed to (#178). Computed
   * by the server for the actor of the token (#121) - it is what lets the apply button say why.
   */
  scheduleConflict: boolean
}

/**
 * What became of a materialized session (#33). A union of literals and not a TS `enum`, for the same
 * reason as `GameTableStatus`: it is what forces a `Record<TableSessionStatus, …>` to cover all
 * three cases when mapping to labels or badge variants.
 */
export type TableSessionStatus = 'Scheduled' | 'Held' | 'Cancelled'

/**
 * Whether somebody was at a session (#36). **The four values do not collapse into two** (#137): a
 * warned absence and a no-show are different facts, and `Unknown` means nobody recorded anything —
 * it stays out of the denominator of every count.
 */
export type AttendanceStatus = 'Present' | 'Absent' | 'Excused' | 'Unknown'

/** One line of a session's roster: who, and what was recorded for them. */
export interface SessionAttendanceEntry {
  userId: string
  userName: string
  attendance: AttendanceStatus
}

/**
 * Mirror of TableSessionResponse — a session as the people running the table see it, with its notes
 * and the whole roster. **It is the base type for sessions**: the player's view derives from it (#3.2).
 */
export interface TableSession {
  id: string
  /** Which session of the run this is, from 1. A cancelled one keeps its number and the replacement takes the next (#194). */
  sequenceNumber: number
  /** When it happens, **in UTC** (#22). The conversion to local time happens once, through `lib/date.ts`. */
  scheduledAt: string
  status: TableSessionStatus
  /** What the master wrote about the session. It never reaches a player. */
  notes: string | null
  attendance: SessionAttendanceEntry[]
}

/**
 * Mirror of PublicSessionResponse — the session as anybody looking at the table sees it: when it is
 * and how it went. Derived from the base type with `Pick` (regla dura 6); neither the notes nor the
 * roster travel here.
 */
export type PublicSession = Pick<TableSession, 'id' | 'sequenceNumber' | 'scheduledAt' | 'status'>

/**
 * Mirror of PlayerSessionResponse — the same session, seen by the person playing. Derived from the
 * base type with `Omit` (regla dura 6): what changes is that it carries neither notes nor anybody
 * else's roster, only *my* attendance (#121).
 */
export type PlayerSession = Omit<TableSession, 'notes' | 'attendance'> & { myAttendance: AttendanceStatus }

/**
 * Mirror of AttendanceSummaryResponse — somebody's historical attendance on a table (#137).
 *
 * **Three numbers and never a percentage**: a ratio would hide exactly the distinction that matters.
 * `registered` is the denominator and counts only the sessions **with something recorded**.
 */
export interface AttendanceSummary {
  present: number
  excused: number
  absent: number
  registered: number
}

/** Mirror of MySessionsResponse — what `/my/tables/:id` reads: my calendar and my attendance. */
export interface MySessions {
  sessions: PlayerSession[]
  summary: AttendanceSummary
}

/**
 * Mirror of UpdateSessionRequest — the master correcting a session.
 *
 * It replaces both fields rather than patching them (#189): absent notes clear them. The date is the
 * one exception — absent means "do not move it", because a session always happens at some instant.
 */
export interface UpdateSessionRequest {
  /** The new instant, **in UTC** (#22), or null to leave the date where it is. */
  scheduledAt?: string | null
  notes?: string | null
}

/** Mirror of AttendanceEntryRequest — one line of the roster on the way in. */
export type AttendanceEntryRequest = Pick<SessionAttendanceEntry, 'userId' | 'attendance'>

/**
 * Mirror of RecordAttendanceRequest. The roster travels whole because that is how it is filled in on
 * screen; anybody left out of the list has their row left alone.
 */
export interface RecordAttendanceRequest {
  attendance: AttendanceEntryRequest[]
}

/**
 * Espejo de TableTypeResponse - cómo se dirige una mesa ("Public", "First class"). Los admins dan de
 * alta el resto desde la aplicación, así que el selector del wizard lo lee de la API y no de una
 * lista escrita a mano.
 */
export interface TableType {
  id: string
  name: string
  /** Qué significa el tipo. Null cuando la fila nunca lo tuvo: "Public" no se explica solo. */
  description: string | null
}

/** Espejo de TableStatusChangeResponse. */
export interface TableStatusChange {
  id: string
  fromStatus: GameTableStatus
  toStatus: GameTableStatus
  changedByName: string
  justification: string | null
  createdAt: string
}

/** Espejo de ChangeTableStatusRequest - usado por cancel/request-changes/pause. */
export interface ChangeTableStatusRequest {
  justification: string
}

/** Espejo de AssignMastersRequest. */
export interface AssignMastersRequest {
  primaryUserId: string
  secondaryUserIds: string[]
}

/**
 * Espejo de AdminTableSummaryResponse - no GameTableSummary: una mesa Unassigned todavía no tiene
 * Primary, así que acá el nombre del master es nullable en vez de forzar esa nulabilidad sobre
 * GameTableCard y las tres pantallas que ya la renderizan con un Primary garantizado.
 */
export interface AdminTableSummary {
  id: string
  name: string
  status: GameTableStatus
  tableTypeName: string | null
  maxPlayers: number | null
  playerCount: number
  primaryMasterName: string | null
  createdAt: string
}
