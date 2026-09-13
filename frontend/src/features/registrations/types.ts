/**
 * Where an application stands. Only three: `Deleted` exists in the database as a soft-delete marker
 * but no response ever carries it, so the union deliberately does not mirror it.
 */
export type RegistrationStatus = 'Candidate' | 'Player' | 'Rejected'

/**
 * A file attached to an application — a character sheet, most commonly (#238).
 *
 * There is no endpoint of its own to read these: F2.2's contract nests them inside
 * `RegistrationResponse` itself, in the same `{fileId, name, mimeType, sizeBytes}` shape every other
 * feature's file rows use — `SubmittedFile` in `features/tasks`, `SharedFile` in `types/file.ts`. A
 * fourth copy of that shape here would be exactly the kind of hand-redeclared variant §3.2 forbids,
 * but it cannot be one of those three: it is a different response, from a different feature, and
 * `Pick`/`Omit` only derive from a type already in scope.
 */
export interface RegistrationFile {
  fileId: string
  name: string
  mimeType: string
  sizeBytes: number
}

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
  /**
   * What the applicant attached, most commonly a character sheet. Empty when nothing was attached —
   * attaching is optional (#238). Neither the master's candidate list nor the applicant's own "my
   * applications" screen can ever add to or remove from this after the application is sent (#238,
   * #247): there is no `PUT` on a registration and no endpoint to detach one of its files.
   */
  attachedFiles: RegistrationFile[]
}

/**
 * Mirror of `TablePlayerResponse` — one player currently at a table.
 *
 * Derived from {@link Registration} rather than declared again (regla dura 6): it is the same three
 * facts about the same person, minus everything that is about the application rather than about
 * them. A roster, not a queue.
 */
export type TablePlayer = Pick<Registration, 'userId' | 'userName' | 'userKarma'>

/**
 * What applying sends. The table comes from the URL and the applicant from the session, so this is
 * the note plus whatever was attached (#121).
 *
 * The files travel by id and **already uploaded** (#238, same split as `features/tasks`'
 * `CreateSubmissionInput`): the review step's confirm is what turns a staged pick into bytes on the
 * server, and this is the shape that goes out once that has happened.
 */
export type CreateRegistrationInput = Pick<Registration, 'description'> & {
  /** The files to attach, by id. */
  fileIds: string[]
}
