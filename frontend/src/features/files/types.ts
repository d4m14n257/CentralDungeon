import type { TableFileType } from '@/types/file'

/**
 * Which lifecycle a file has. What the platform published, what waits in the platform's library
 * unpublished or hidden, what its owner keeps to reuse, and what was uploaded for one
 * context and is transient.
 *
 * A union of literals rather than a TypeScript `enum`. Note that the wire
 * value is the backend constant's name — `SingleUse` — and not the hyphenated `Single-use` the
 * column holds; that spelling stops at the backend's converter and never reaches here.
 */
export type FileType = 'Public' | 'Library' | 'Private' | 'SingleUse'

/**
 * The flow a file belongs to — its category.
 *
 * **It says where a file is used, not what the document is.** Nobody uploading can honestly declare
 * "this is a character sheet template": what a master asks for might be a form in a `.doc`. What the
 * system knows for certain is which screen the file came from, so the category is observed and never
 * asked — the one exception is `/my/files`, where there is no flow to observe.
 *
 * A file belongs to **as many categories as it has been used in**, and a membership is never revoked:
 * detaching a file from a table says it is no longer that table's, never that it was never its
 * material. That is why this is a list everywhere and never a single value.
 *
 * There is no public audience: the flow already says who a document is for.
 */
export type FileCategory = 'TableMaterial' | 'MasterRequest' | 'PlayerApplication' | 'PlayerSubmission' | 'Announcement'

/**
 * One place a file is linked **right now**.
 *
 * Not the same question as a category, though it names one: a category is what the file has belonged to and
 * never goes away, a use is a live link and vanishes when the link does.
 */
export interface FileUsage {
  category: FileCategory
  /** The table the use belongs to — for a submission or a request too: the one whose task it is. */
  contextId: string
  /** The table's name, which is what a person actually recognises their own file by. */
  contextName: string
  /**
   * How the reader is joined to that table, said by the backend because directing a table is not
   * deduced from the role. `Master` wins when both hold; `null` means the name has no
   * view of its own to open.
   */
  membership: 'Master' | 'Player' | null
}

/** Whether a file still counts. Marking is the only delete there is — the bytes are freed later by the platform owner. */
export type FileStatus = 'Current' | 'Deleted'

export type { CommitResult, CommitStagedFiles, SharedFile, StagedFile, TableFileType } from '@/types/file'

/**
 * **The base type of this feature**. Mirror of the backend's `FileResponse`: a file as
 * its owner sees it, which is what an upload answers with and what the reuse history lists.
 *
 * Everything else below is derived from this with utility types, never re-declared by hand.
 */
export interface StoredFile {
  id: string
  /** The original filename. Metadata only — it never touched the filesystem on the way in. */
  name: string
  mimeType: string
  /** The size as uploaded, before compression. What is shown, and what the cap applies to. */
  sizeBytes: number
  fileType: FileType
  /**
   * The categories it belongs to. Plural and cumulative. **Only `/files/mine` fills this in** —
   * an upload and a single-file read answer with an empty array.
   */
  categories: FileCategory[]
  /**
   * Where the file is linked right now. Same rule: only the owner's own list pays for it.
   */
  usages: FileUsage[]
  /** ISO-8601 UTC, or null when never recorded. The conversion to the reader's zone is ours. */
  lastUsedAt: string | null
  /** ISO-8601 UTC. */
  createdAt: string
}

/** Mirror of `MyFileCategoriesResponse` — the categories this person may file something of their own under. */
export interface MyFileCategories {
  categories: FileCategory[]
}

/**
 * What an upload answers with: the file, and whether it was recognised rather than written.
 *
 * The flag is a fact about *this request* and not about the file, which is why it rides alongside
 * `StoredFile` instead of on it. It exists so the screen can say "you already had this" — before it existed
 * the two cases were the same 201 and deduplication happened in complete silence.
 */
export interface UploadedFile {
  file: StoredFile
  deduplicated: boolean
}

/** Mirror of `AdminFileTableResponse` — a table using a file, named so the screen can link it. */
export interface AdminFileTable {
  id: string
  name: string
}

/**
 * Mirror of `AdminFileResponse` — the /admin/files row.
 *
 * It extends the base shape minus `usages` (the owner's own list is the only one that carries it): an
 * admin sees what an owner sees plus the fields nobody else has business seeing — whose it is, which
 * tables use it, and whether it was marked gone.
 */
export interface AdminFile extends Omit<StoredFile, 'usages'> {
  ownerId: string
  /** How to name the uploader on screen — their Discord username, which everybody has. */
  ownerName: string
  /**
   * The tables that hold a live link to it. **This is where sharing one file stops being a claim**: one file
   * used by three tables reads as one row with three tables, not as three files. The number of uses is
   * its length.
   */
  tables: AdminFileTable[]
  status: FileStatus
  /**
   * When it was first published into the platform's library, or null if it never was. With
   * `fileType`, it tells the library's states apart — see `libraryStateOf`.
   */
  publishedAt: string | null
}

/**
 * Mirror of `PublicFileResponse` — what the platform published, as the picker offers it.
 *
 * Deliberately narrower than {@link StoredFile}: choosing the community's default character sheet
 * needs its name and its size, not who uploaded it or when it was last touched.
 */
export type PublicFile = Pick<StoredFile, 'id' | 'name' | 'mimeType' | 'sizeBytes' | 'categories'>

/**
 * Mirror of `TableFileResponse` — one row of the master's Archivos tab.
 *
 * It carries the file *and* the link, because the two say different things and the screen needs
 * both: `isPrivate` is about this attachment, `fileType` about the file itself.
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

/** What an upload sends alongside the bytes. `Public` is an admin's to grant, never an uploader's. */
export interface UploadFileInput {
  fileType: Extract<FileType, 'Private' | 'SingleUse'>
  /**
   * The category to put it in, or **null** — which is the normal case. A file uploaded to attach
   * to a table or to answer a request is classified by the link that follows, because the flow knows
   * what the uploader cannot be asked to declare. Only `/my/files` sends one.
   */
  fileCategory: FileCategory | null
}

/**
 * What renaming sends. Both fields always travel, so the request describes the state the file should
 * end in rather than a delta: "null means leave it alone" makes clearing a value impossible to express.
 */
export type UpdateFileInput = Pick<StoredFile, 'name'> & {
  /** True to keep it in the reuse history, which is the "save this for later" option. */
  keepInLibrary: boolean
}

/** What attaching sends. The file is linked, never copied. */
export type LinkTableFileInput = Pick<TableFile, 'fileId' | 'tableFileType' | 'isPrivate'>

/** What changing an attachment sends. Nothing here can reach the file itself. */
export type UpdateTableFileInput = Pick<TableFile, 'tableFileType' | 'isPrivate'>

/**
 * What an admin's upload into the platform's library sends with the bytes: what the file
 * is, always, and whether it goes out now — a published file is attached by tables straight away and
 * hiding it later does not take it off them, so that is a choice, never a side effect.
 */
export interface LibraryUploadInput extends Pick<AdminFile, 'categories'> {
  /** True to publish it on upload; false to leave it in the library unpublished. */
  publish: boolean
}

/** What changing the category of an unpublished file of the library sends: one, like the upload. */
export interface LibraryCategoryInput {
  /** What the file is now. One of the three publishable categories. */
  category: FileCategory
}

/**
 * What an admin's upload into the platform's library produced: the published row, and whether
 * the admin already had that content.
 */
export interface AdminUploadedFile {
  file: AdminFile
  /** True when the content was recognised and the admin's existing row came back published. */
  deduplicated: boolean
}
