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

/** Who a published file is meant for (#64). Null on anything that is not `Public`. */
export type PublicAudience = 'Masters' | 'Players' | 'Announcements'

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
  publicAudience: PublicAudience | null
  /** ISO-8601 UTC, or null when never recorded. The conversion to the reader's zone is ours (#22, #111). */
  lastUsedAt: string | null
  /** ISO-8601 UTC. */
  createdAt: string
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
export type PublicFile = Pick<StoredFile, 'id' | 'name' | 'mimeType' | 'sizeBytes'> & {
  publicAudience: PublicAudience
}

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

/** What publishing sends: who the file is for (#64). The audience is not optional — that is M24.1's fix. */
export type PublishFileInput = Pick<AdminFile, 'publicAudience'> & { publicAudience: PublicAudience }
