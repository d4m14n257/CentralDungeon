/**
 * Where an application stands. Four of them: `Deleted` exists in the database as a soft-delete
 * marker but no response ever carries it, so the union deliberately does not mirror it.
 *
 * **`Blocked` is the veto** (#29, #39, F3.4), and unlike `Deleted` it **does** travel — to the
 * masters of the table, and to nobody else. Whoever is vetoed stops seeing the table at all: the
 * explorer omits it, the detail answers `404` rather than `403`, and even the files they used to
 * download do. A status they could read back would be the rendija that undoes all of it.
 *
 * Which is also why the master's roster keeps showing the row (see {@link TablePlayer}): the veto is
 * reversible, and a veto that disappears from the screen is not reversible in practice.
 */
export type RegistrationStatus = 'Candidate' | 'Player' | 'Rejected' | 'Blocked'

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
  /**
   * Who applied the veto, and when. Both null on every row that is not `Blocked` (#39).
   *
   * **They travel to the masters of the table and never to the person vetoed**, who by then does not
   * see the table at all. What they are for is the other half of #39: a veto has to be reversible,
   * and reversing one you cannot see the author of is a decision taken blind.
   */
  blockedByName: string | null
  blockedAt: string | null
  /**
   * The reason that was written down when the veto was applied (#39), or null when the row is not
   * vetoed.
   *
   * **It is the point of making the reason obligatory.** A veto is reversible, and reversing one
   * means somebody later — often a different master — reading why it happened. Storing a reason
   * nothing ever displays would be asking for it as a formality.
   */
  blockJustification: string | null
}

/**
 * Mirror of `TablePlayerResponse` — one row of a table's roster, as its masters see it.
 *
 * Derived from {@link Registration} rather than declared again (regla dura 6): it is the same facts
 * about the same person, minus everything that is about the application rather than about them. A
 * roster, not a queue.
 *
 * **It grew with F3.4, and the veto is why.** Until then a roster was three read-only facts; now it
 * is the screen the veto is applied and lifted from, so it carries what those two acts need:
 *
 * - `registrationId`, because the veto acts on the **application** and not on the person — vetoing
 *   is per table (#29), and the same account may be playing happily at four others.
 * - `status`, because a vetoed row **stays in the list**. Dropping it would hide the one row with an
 *   action left on it, and an irreversible veto is exactly what #39 refused to build.
 * - `blockedByName`, `blockedAt` and `blockJustification`, which are what whoever is about to lift
 *   one reads first: whose decision it was, how long ago, and why.
 */
export type TablePlayer = Pick<
  Registration,
  'userId' | 'userName' | 'userKarma' | 'status' | 'blockedByName' | 'blockedAt' | 'blockJustification'
> & {
  /** The application this row is: what the two acts of the veto address. */
  registrationId: Registration['id']
}

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
