/**
 * Where an application stands. Only three: `Deleted` exists in the database as a soft-delete marker
 * but no response ever carries it, so the union deliberately does not mirror it.
 */
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
  /** The master's own words, shown exactly as typed. Null when the system did the rejecting. */
  rejectionJustification: string | null
  /**
   * The code of a rejection the application wrote itself, today only `TABLE_FULL` (#34). Rendered in
   * the reader's language (#197). Null whenever a person did the rejecting.
   */
  rejectionReasonCode: string | null
}

/**
 * What applying sends. The table comes from the URL and the applicant from the session, so the note
 * is all that is left (#121).
 */
export type CreateRegistrationInput = Pick<Registration, 'description'>
