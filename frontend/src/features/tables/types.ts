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
   * El calendario materializado (#26, #33): fechas, no la forma semanal. Viaja con el detalle y no
   * en un endpoint propio porque esta lectura ya decide quién puede ver la mesa. Vacío hasta que la
   * mesa abre, y en pausa trae solo lo que ya pasó (#32).
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
 * Lo que le pasó a una sesión materializada (#33). Unión de literales y no un `enum` de TS, por el
 * mismo motivo que `GameTableStatus`: es lo que obliga a un `Record<TableSessionStatus, …>` a cubrir
 * los tres casos al mapear a etiquetas o a variantes de badge.
 */
export type TableSessionStatus = 'Scheduled' | 'Held' | 'Cancelled'

/**
 * Si alguien estuvo en una sesión (#36). **Los cuatro valores no se colapsan en dos** (#137): una
 * falta avisada y un plantón son hechos distintos, y `Unknown` significa que nadie registró nada —
 * queda fuera del denominador de todo conteo.
 */
export type AttendanceStatus = 'Present' | 'Absent' | 'Excused' | 'Unknown'

/** Una línea del padrón de una sesión: quién, y qué se registró de esa persona. */
export interface SessionAttendanceEntry {
  userId: string
  userName: string
  attendance: AttendanceStatus
}

/**
 * Espejo de TableSessionResponse — una sesión como la ve quien dirige la mesa, con sus notas y el
 * padrón completo. **Es el tipo base de las sesiones**: la vista del jugador se deriva de él (#3.2).
 */
export interface TableSession {
  id: string
  /** Qué sesión de la tanda es, desde 1. Una cancelada conserva su número y la reposición toma el siguiente (#194). */
  sequenceNumber: number
  /** Cuándo ocurre, **en UTC** (#22). La conversión a hora local pasa una sola vez, por `lib/date.ts`. */
  scheduledAt: string
  status: TableSessionStatus
  /** Lo que el master escribió sobre la sesión. Nunca llega a un jugador. */
  notes: string | null
  attendance: SessionAttendanceEntry[]
}

/**
 * Espejo de PublicSessionResponse — la sesión como la ve cualquiera que mire la mesa: cuándo es y
 * cómo salió. Derivada del tipo base con `Pick` (regla dura 6); ni las notas ni el padrón viajan acá.
 */
export type PublicSession = Pick<TableSession, 'id' | 'sequenceNumber' | 'scheduledAt' | 'status'>

/**
 * Espejo de PlayerSessionResponse — la misma sesión, vista por quien juega. Derivada del tipo base
 * con `Omit` (regla dura 6): lo que cambia es que no lleva notas ni el padrón ajeno, solo *mi*
 * asistencia (#121).
 */
export type PlayerSession = Omit<TableSession, 'notes' | 'attendance'> & { myAttendance: AttendanceStatus }

/**
 * Espejo de AttendanceSummaryResponse — la asistencia histórica de alguien en una mesa (#137).
 *
 * **Tres números y nunca un porcentaje**: una razón escondería justo la distinción que importa.
 * `registered` es el denominador y cuenta solo las sesiones **con algo registrado**.
 */
export interface AttendanceSummary {
  present: number
  excused: number
  absent: number
  registered: number
}

/** Espejo de MySessionsResponse — lo que lee `/my/tables/:id`: mi calendario y mi asistencia. */
export interface MySessions {
  sessions: PlayerSession[]
  summary: AttendanceSummary
}

/**
 * Espejo de UpdateSessionRequest — el master corrigiendo una sesión.
 *
 * Reemplaza los dos campos, no parchea (#189): unas notas ausentes las vacían. La fecha es la única
 * excepción — ausente significa «no la muevas», porque una sesión siempre ocurre en algún instante.
 */
export interface UpdateSessionRequest {
  /** El instante nuevo, **en UTC** (#22), o null para dejar la fecha donde está. */
  scheduledAt?: string | null
  notes?: string | null
}

/** Espejo de AttendanceEntryRequest — una línea del padrón de entrada. */
export type AttendanceEntryRequest = Pick<SessionAttendanceEntry, 'userId' | 'attendance'>

/**
 * Espejo de RecordAttendanceRequest. El padrón viaja entero porque así se completa en pantalla; a
 * quien se deja afuera de la lista no se le toca la fila.
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
