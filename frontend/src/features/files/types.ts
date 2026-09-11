import type { TableFileType } from '@/types/file'

/**
 * Which lifecycle a file has (#68). Three, not two: what the platform published, what its owner
 * keeps to reuse (#65), and what was uploaded for one context and is transient.
 *
 * A union of literals rather than a TypeScript `enum` (arquitectura.md 3.2). Note that the wire
 * value is the backend constant's name — `SingleUse` — and not the hyphenated `Single-use` the
 * column holds; that spelling stops at the backend's converter and never reaches here.
 */
export type FileType = 'Public' | 'Private' | 'SingleUse'

/**
 * The flow a file belongs to (#233) — its cajón.
 *
 * **It says where a file is used, not what the document is.** Nobody uploading can honestly declare
 * "this is a character sheet template": what a master asks for might be a form in a `.doc`. What the
 * system knows for certain is which screen the file came from, so the cajón is observed and never
 * asked — the one exception is `/my/files`, where there is no flow to observe.
 *
 * A file belongs to **as many cajones as it has been used in**, and a membership is never revoked:
 * detaching a file from a table says it is no longer that table's, never that it was never its
 * material. That is why this is a list everywhere and never a single value.
 *
 * `public_audience` is gone (#64 derogated): the flow already says who a document is for.
 */
export type FileCategory = 'TableMaterial' | 'MasterRequest' | 'PlayerApplication' | 'PlayerSubmission' | 'Announcement'

/**
 * One place a file is linked **right now** (#232).
 *
 * Not the same question as a cajón, though it names one: a cajón is what the file has belonged to and
 * never goes away, a use is a live link and vanishes when the link does.
 */
export interface FileUsage {
  category: FileCategory
  /** The table the use belongs to — for a submission or a request too: the one whose task it is. */
  contextId: string
  /** The table's name, which is what a person actually recognises their own file by. */
  contextName: string
}

/** Whether a file still counts. Marking is the only delete F1 has — the bytes wait for F5 (#25, #66). */
export type FileStatus = 'Current' | 'Deleted'

export type { SharedFile, TableFileType } from '@/types/file'

/**
 * **The base type of this feature** (regla dura 6). Mirror of the backend's `FileResponse`: a file as
 * its owner sees it, which is what an upload answers with and what the reuse history lists.
 *
 * Everything else below is derived from this with utility types, never re-declared by hand.
 */
export interface StoredFile {
  id: string
  /** The original filename. Metadata only — it never touched the filesystem on the way in (#80). */
  name: string
  mimeType: string
  /** The size as uploaded, before compression (#75). What is shown, and what the cap applies to. */
  sizeBytes: number
  fileType: FileType
  /**
   * The cajones it belongs to (#233). Plural and cumulative. **Only `/files/mine` fills this in** —
   * an upload and a single-file read answer with an empty array.
   */
  categories: FileCategory[]
  /**
   * Where the file is linked right now (#232). Same rule: only the owner's own list pays for it.
   */
  usages: FileUsage[]
  /** ISO-8601 UTC, or null when never recorded. The conversion to the reader's zone is ours (#22, #111). */
  lastUsedAt: string | null
  /** ISO-8601 UTC. */
  createdAt: string
}

/**
 * What an upload answers with: the file, and whether it was recognised rather than written (#234).
 *
 * The flag is a fact about *this request* and not about the file, which is why it rides alongside
 * `StoredFile` instead of on it. It exists so the screen can say "you already had this" — until #234
 * the two cases were the same 201 and deduplication happened in complete silence.
 */
export interface UploadedFile {
  file: StoredFile
  deduplicated: boolean
}

/**
 * Mirror of `AdminFileResponse` — the /admin/files row.
 *
 * It extends the base shape rather than restating it: an admin sees what an owner sees plus the four
 * fields nobody else has business seeing — whose it is, how many tables use it, and whether it was
 * marked gone.
 */
export interface AdminFile extends StoredFile {
  ownerId: string
  /** How to name the uploader on screen — their Discord username, which everybody has. */
  ownerName: string
  /**
   * How many tables hold a live link to it. **This is where #79 stops being a claim**: one file used
   * by three tables reads as one row with three uses, not as three files.
   */
  uses: number
  status: FileStatus
}

/**
 * Mirror of `PublicFileResponse` — what the platform published, as the picker offers it (#64, #79).
 *
 * Deliberately narrower than {@link StoredFile}: choosing the community's default character sheet
 * needs its name and its size, not who uploaded it or when it was last touched.
 */
export type PublicFile = Pick<StoredFile, 'id' | 'name' | 'mimeType' | 'sizeBytes' | 'categories'>

/**
 * Mirror of `TableFileResponse` — one row of the master's Archivos tab.
 *
 * It carries the file *and* the link, because the two say different things and the screen needs
 * both: `isPrivate` is about this attachment, `fileType` about the file itself (#79).
 */
export interface TableFile extends Pick<StoredFile, 'name' | 'mimeType' | 'sizeBytes' | 'fileType'> {
  fileId: string
  tableFileType: TableFileType
  /**
   * Whether only the people running **this** table see it. About the link: the same file can be
   * shared on one table and private on another.
   */
  isPrivate: boolean
  /** Whether the actor uploaded it — what tells the screen it may offer to rename or delete the file. */
  isOwnedByMe: boolean
  /** ISO-8601 UTC, or null on a row written before the column existed. */
  attachedAt: string | null
}

/** What an upload sends alongside the bytes. `Public` is an admin's to grant, never an uploader's (#64). */
export interface UploadFileInput {
  fileType: Extract<FileType, 'Private' | 'SingleUse'>
  /**
   * The cajón to put it in (#233), or **null** — which is the normal case. A file uploaded to attach
   * to a table or to answer a request is classified by the link that follows, because the flow knows
   * what the uploader cannot be asked to declare. Only `/my/files` sends one.
   */
  fileCategory: FileCategory | null
}

/**
 * What renaming sends. Both fields always travel, so the request describes the state the file should
 * end in rather than a delta — same reasoning as #189.
 */
export type UpdateFileInput = Pick<StoredFile, 'name'> & {
  /** True to keep it in the reuse history (#65), which is the "save this for later" of #68. */
  keepInLibrary: boolean
}

/** What attaching sends. The file is linked, never copied (#79). */
export type LinkTableFileInput = Pick<TableFile, 'fileId' | 'tableFileType' | 'isPrivate'>

/** What changing an attachment sends. Nothing here can reach the file itself. */
export type UpdateTableFileInput = Pick<TableFile, 'tableFileType' | 'isPrivate'>

/**
 * What publishing sends: the cajones the file is offered in (#233).
 *
 * **Plural, and that is the point.** The community's blank sheet is asked for while a table recruits
 * *and* once it is running, so it is published into `TableMaterial` and `MasterRequest` at once — one
 * file, two rows. It replaced the audience of #64 outright: a flow already says who a document is for.
 */
export type PublishFileInput = Pick<AdminFile, 'categories'>
