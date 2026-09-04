/**
 * Who a task is addressed to (#63). A union of literals rather than a TypeScript `enum`
 * (arquitectura.md §3.2).
 *
 * The three moments a table asks for something: before you are in, once you are in, and to you in
 * particular.
 */
export type TaskAudience = 'Candidates' | 'Players' | 'Single'

/**
 * Where a task is in its life. `Deleted` exists in the database as a soft-delete marker but no
 * response ever carries it, so the union deliberately does not mirror it — the same choice
 * `RegistrationStatus` made.
 */
export type TaskStatus = 'Open' | 'Closed'

/**
 * A file handed in with an answer. Mirror of the backend's `SubmittedFileResponse`.
 *
 * Narrower than the file types of `features/files`: what is needed to show a row and open it, and
 * nothing about who owns it or when it was last touched.
 */
export interface SubmittedFile {
  fileId: string
  /** The original filename — metadata only, it never touched the filesystem on the way in (#80). */
  name: string
  mimeType: string
  /** The size as uploaded, before compression (#75). */
  sizeBytes: number
}

/**
 * **The base type of this feature** (regla dura 6). Mirror of the backend's `TaskResponse`: a task as
 * the people running the table see it, which is what the Peticiones tab lists.
 *
 * Everything else below is derived from this with utility types, never re-declared by hand.
 */
export interface TableTask {
  taskId: string
  gameTableId: string
  /** The session this is tied to, or null for "at any point" — the common case (#63). */
  tableSessionId: string | null
  /** Which session of the run that is, from 1, so the screen can name it. Null together with the id. */
  sessionSequenceNumber: number | null
  audience: TaskAudience
  /** The one person addressed. Null on any audience but `Single`. */
  targetUserId: string | null
  targetUserName: string | null
  title: string
  /** Sanitized rich text (#62), rendered through `RichTextView`. Null when the title said it all. */
  description: string | null
  acceptsText: boolean
  acceptsFiles: boolean
  /**
   * Whether the master labelled it indispensable. **It is a label** (#70): the screen says so, and
   * nothing in the application refuses, blocks or removes anybody over it.
   */
  isMandatory: boolean
  /** ISO-8601 UTC, or null for no date. Nothing happens when it passes (#22, #70). */
  dueAt: string | null
  status: TaskStatus
  /** How many answers came in. Answers accumulate, so it can exceed the number of people (#76). */
  submissionCount: number
  /** How many different people answered. This is the number that means "handed in". */
  respondentCount: number
  /** How many were asked, derived from the audience at read time and never stored. */
  recipientCount: number
  /** ISO-8601 UTC. Publishing and creating are the same act — there is no draft (#77). */
  createdAt: string
}

/**
 * Mirror of `ApplicableTaskResponse` — a task as the person being asked sees it, on `/tables/:id` and
 * `/my/tables/:id`.
 *
 * Deliberately narrower than {@link TableTask}: no counts and no roster. How many other people handed
 * in their sheet is the master's information, not a recipient's. What it adds is about the reader
 * themselves.
 */
export type ApplicableTask = Pick<
  TableTask,
  | 'taskId'
  | 'audience'
  | 'tableSessionId'
  | 'sessionSequenceNumber'
  | 'title'
  | 'description'
  | 'acceptsText'
  | 'acceptsFiles'
  | 'isMandatory'
  | 'dueAt'
  | 'createdAt'
> & {
  /**
   * Whether this reader may answer right now: they are in the audience and the task is still open.
   * It is what lets the button say *why* it is not offered, instead of being absent for no stated
   * reason (principio 2 de frontend-diseno.md §1).
   */
  canSubmit: boolean
  /** How many times **this** reader already answered. A count, because answers accumulate (#76). */
  mySubmissionCount: number
}

/** Mirror of `TaskRecipientResponse` — somebody a task was addressed to. */
export interface TaskRecipient {
  userId: string
  /** Their Discord username, which everybody has. */
  userName: string
}

/**
 * Mirror of `TaskSubmissionResponse` — one answer somebody handed in.
 *
 * There is no verdict on it and there never will be: the system records that something arrived and
 * stops there (#76).
 */
export interface TaskSubmission {
  submissionId: string
  taskId: string
  userId: string
  userName: string
  /** Sanitized rich text (#62), or null when the answer was files only. */
  content: string | null
  files: SubmittedFile[]
  /** ISO-8601 UTC. */
  submittedAt: string | null
}

/**
 * Mirror of `TaskSubmissionsResponse` — what came in and who has not answered.
 *
 * The two halves travel together because they are read as one thought: "seven of nine".
 */
export interface TaskSubmissions {
  taskId: string
  submissions: TaskSubmission[]
  /** The people still to answer. **A list to talk to, not one to act on** (#70). */
  missing: TaskRecipient[]
  recipientCount: number
}

/**
 * What publishing sends. Every field always travels, so the request describes the state the task
 * should end in rather than a delta — same reasoning as #189.
 */
export type CreateTaskInput = Pick<
  TableTask,
  'title' | 'description' | 'audience' | 'targetUserId' | 'tableSessionId' | 'acceptsText' | 'acceptsFiles' | 'isMandatory' | 'dueAt'
>

/** What correcting sends. The same shape: a full replacement, never a patch (#189). */
export type UpdateTaskInput = CreateTaskInput

/**
 * What handing in an answer sends.
 *
 * The files are **already uploaded** and travel by id (#65, #79) — reusing the character sheet from
 * somebody's history rather than storing it again is the point of the whole file feature.
 */
export interface CreateSubmissionInput {
  /** The written answer as rich text, or null when the answer is files only. */
  content: string | null
  fileIds: string[]
}
