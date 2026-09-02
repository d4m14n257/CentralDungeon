export type GameTableStatus =
  'Unassigned' | 'Preparation' | 'ChangesRequested' | 'Opened' | 'InProgress' | 'PauseRequested' | 'Pause' | 'Finished' | 'Canceled'

export interface MasterSummary {
  userId: string
  name: string
  karma: number
  masterType: 'Primary' | 'Secondary'
}

/** Espejo de GameTableSummaryResponse. */
export interface GameTableSummary {
  id: string
  name: string
  status: GameTableStatus
  tableTypeName: string | null
  maxPlayers: number | null
  playerCount: number
  primaryMaster: MasterSummary
}

/** Espejo de CreateGameTableRequest - forma de entrada, no se deriva de GameTableDetail (tableTypeId vs tableTypeName). */
export interface CreateGameTableRequest {
  name: string
  description?: string | null
  requirements?: string | null
  tableTypeId?: string | null
  startDate?: string | null
  duration?: string | null
  totalSessions?: number | null
  maxPlayers?: number | null
}

/** Espejo de GameTableDetailResponse. */
export interface GameTableDetail {
  id: string
  name: string
  description: string | null
  requirements: string | null
  tableTypeName: string | null
  status: GameTableStatus
  maxPlayers: number | null
  playerCount: number
  startDate: string | null
  duration: string | null
  totalSessions: number | null
  masters: MasterSummary[]
  createdAt: string
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
