export type RegistrationStatus = 'Candidate' | 'Player' | 'Rejected'

/** Espejo de RegistrationResponse. */
export interface Registration {
  id: string
  gameTableId: string
  gameTableName: string
  userId: string
  userName: string
  userKarma: number
  status: RegistrationStatus
  description: string | null
  createdAt: string
  rejectionJustification: string | null
}

export type CreateRegistrationInput = Pick<Registration, 'description'>
