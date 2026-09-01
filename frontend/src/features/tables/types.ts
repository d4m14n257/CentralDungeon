export type GameTableStatus = 'Preparation' | 'Opened' | 'InProgress'

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
