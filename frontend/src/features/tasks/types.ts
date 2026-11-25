import type { StagedFile } from '@/types/file'
/**
 * Who a task is addressed to. A union of literals rather than a TypeScript `enum`.
 *
 * The three moments a table asks for something: before you are in, once you are in, and to you in
 * particular.
 */
export type TaskAudience = 'Candidates' | 'Players' | 'Single'

/**
 * Where a task is in its life: taking answers, or closed.
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
  /** The original filename — metadata only, it never touched the filesystem on the way in. */
  name: string
  mimeType: string
  /** The size as uploaded, before compression. */
  sizeBytes: number
}

/**
 * **The base type of this feature**. Mirror of the backend's `TaskResponse`: a task as
 * the people running the table see it, which is what the Peticiones tab lists.
 *
 * Everything else below is derived from this with utility types, never re-declared by hand.
 */
export interface TableTask {
  taskId: string
  gameTableId: string
  /** The session this is tied to, or null for "at any point" — the common case. */
  tableSessionId: string | null
  /** Which session of the run that is, from 1, so the screen can name it. Null together with the id. */
  sessionSequenceNumber: number | null
  audience: TaskAudience
  /** The one person addressed. Null on any audience but `Single`. */
  targetUserId: string | null
  targetUserName: string | null
  title: string
  /** Sanitized rich text, rendered through `RichTextView`. Null when the title said it all. */
  description: string | null
  acceptsText: boolean
  acceptsFiles: boolean
  /**
   * Whether the master labelled it indispensable. **It is a label**: the screen says so, and
   * nothing in the application refuses, blocks or removes anybody over it.
   */
  isMandatory: boolean
  /**
   * The blanks the master attached — the forms the ask is about.
   *
   * **The half of a request the model never had.** `acceptsFiles` says whether an *answer* may carry
   * files; this is what the *asking* carries. Without it, "send me your sheet on this form" arrived
   * with no form and the master had to describe it in prose.
   */
  files: SubmittedFile[]
  /** ISO-8601 UTC, or null for no date. Nothing happens when it passes. */
  dueAt: string | null
  status: TaskStatus
  /** How many answers came in. Answers accumulate, so it can exceed the number of people. */
  submissionCount: number
  /** How many different people answered. This is the number that means "handed in". */
  respondentCount: number
  /** How many were asked, derived from the audience at read time and never stored. */
  recipientCount: number
  /** ISO-8601 UTC. Publishing and creating are the same act — there is no draft. */
  createdAt: string
}

/**
 * Mirror of `ApplicableTaskResponse` — a task as the person being asked sees it, on `/player/tables/:id` and
 * `/player/my-tables/:id`.
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
  | 'files'
  | 'dueAt'
  | 'createdAt'
> & {
  /**
   * Whether this reader may answer right now: they are in the audience and the task is still open.
   * It is what lets the button say *why* it is not offered, instead of being absent for no stated
   * reason.
   */
  canSubmit: boolean
  /** How many times **this** reader already answered. A count, because answers accumulate. */
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
 * stops there.
 */
export interface TaskSubmission {
  submissionId: string
  taskId: string
  userId: string
  userName: string
  /** Sanitized rich text, or null when the answer was files only. */
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
  /** The people still to answer. **A list to talk to, not one to act on**. */
  missing: TaskRecipient[]
  recipientCount: number
}

/**
 * What publishing sends. Every field always travels, so the request describes the state the task
 * should end in rather than a delta: "null means leave it alone" makes clearing a value impossible to express.
 */
export type CreateTaskInput = Pick<
  TableTask,
  'title' | 'description' | 'audience' | 'targetUserId' | 'tableSessionId' | 'acceptsText' | 'acceptsFiles' | 'isMandatory' | 'dueAt'
> & {
  /**
   * The blanks to attach, by id. Linked and never copied, so the community's published
   * form is attached rather than re-uploaded by every master.
   */
  fileIds: string[]
}

/**
 * What the request form hands up, before anything has been uploaded.
 *
 * Same split as {@link SubmissionDraft}, and for the same reason: the wire keeps `fileIds` because
 * that is what the backend takes, and the form says `staged` because that is what it has.
 */
export type TaskDraft = Omit<CreateTaskInput, 'fileIds'> & { staged: StagedFile[] }

/** What correcting sends. The same shape: a full replacement, never a patch. */
export type UpdateTaskInput = CreateTaskInput

/**
 * What handing in an answer sends.
 *
 * The files are **already uploaded** and travel by id — reusing the character sheet from
 * somebody's history rather than storing it again is the point of the whole file feature.
 */
export interface CreateSubmissionInput {
  /** The written answer as rich text, or null when the answer is files only. */
  content: string | null
  /** The files to hand in, by id. Already uploaded by the time this travels. */
  fileIds: string[]
}

/**
 * What the answer dialog hands up, before anything has been uploaded.
 *
 * **The draft and the request are two different shapes on purpose.** Nothing reaches the server
 * until the screen that owns the send uploads the staged files and turns them into ids — so the
 * request body can keep saying `fileIds`, which is what the backend takes, and the dialog can say
 * `staged`, which is what it actually has. Collapsing them is how a `File` ends up being serialized
 * into a JSON body.
 */
export interface SubmissionDraft {
  content: string | null
  staged: StagedFile[]
}
