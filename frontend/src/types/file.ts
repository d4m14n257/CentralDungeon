/**
 * The wire shape of a file a table shares, in the root layer rather than inside `features/files`.
 *
 * It lives here for exactly the reason `types/catalog.ts` does: **two features need it** and a
 * feature never imports another (§3.1.2, regla dura 16). `files` owns everything about a file, and
 * `tables` receives these nested inside a table's detail — a table arrives already carrying what it
 * shares, which is one round trip instead of two and which inherits the veto check that read already
 * performs (#29).
 *
 * The alternative was re-declaring the same five fields in `tables`, and that is exactly what "un
 * tipo base por entidad" (§3.2) forbids: the day the backend adds a field, one of the two copies
 * would keep describing the old shape.
 */

/**
 * What a file is doing on a table: prepared beforehand, or produced at a session.
 *
 * A union of literals rather than a TS `enum`, because the backend serializes it as a string and
 * because `Record<TableFileType, …>` then forces every case to be covered when mapping it to a label
 * (§3.2 regla 9).
 */
export type TableFileType = 'Preparation' | 'Session'

/**
 * Mirror of `SharedFileResponse`: a table's file as a candidate or a player sees it, read-only.
 *
 * Deliberately narrow. There is no `isPrivate` here because **a private attachment is absent from
 * this list rather than listed and hidden** — a reader has no reason to know their master keeps notes
 * — and no owner, because who uploaded the map is not something anybody needs in order to open it
 * (#79).
 */
export interface SharedFile {
  /** The file's identifier, which is what the download endpoint takes. */
  fileId: string
  /** The original filename — what the reader recognises it by. Metadata only (#80). */
  name: string
  /** The declared MIME type, so a screen can show the right icon. */
  mimeType: string
  /**
   * The size as uploaded, before compression (#75). Shown because somebody on a phone deserves to
   * know what a tap is about to cost them.
   */
  sizeBytes: number
  tableFileType: TableFileType
}

/**
 * A file waiting to be sent (#238).
 *
 * **In the root layer and not inside `features/files`**, for the reason `SharedFile` is here: two
 * features need it and a feature never imports another (§3.1.2, regla dura 16). `files` owns the
 * picker that produces one and the hook that sends it; `tasks` holds a list of them while somebody
 * writes a request or an answer, and hands it back when they press send.
 *
 * **Nothing reaches the server until the operation is confirmed.** Uploading the moment a file was
 * picked meant abandoning a four-step wizard left it on the server anyway. So a pick stages, and the
 * confirm that creates the table — or sends the answer, or publishes the request — is what uploads.
 *
 * Two shapes, because only one of them has anything to upload: `new` is bytes still in the browser,
 * `existing` is something reused from the history (#65) or published by the platform (#79), which
 * already has an id. `localId` exists only to key the list and remove one before sending — two files
 * with the same name are two entries, and `name` cannot tell them apart.
 */
export type StagedFile = { kind: 'new'; localId: string; name: string; file: File } | { kind: 'existing'; fileId: string; name: string }

/** The key that identifies one staged entry in a list: its local id, or the file's id. */
export function stagedKey(staged: StagedFile): string {
  return staged.kind === 'new' ? staged.localId : staged.fileId
}

/**
 * What came back from sending a staged list (#238).
 *
 * **`failed` is not an exception**, and that is the whole shape of it: a file that could not be
 * uploaded does not undo the rest. The table is created, the answer is sent, and what did upload is
 * linked — because losing four steps of work over one bad file is worse than the missing file, and
 * every flow that produces one of these can be completed by editing afterwards. The names are what
 * the screen says, so somebody knows exactly what to add.
 */
export interface CommitResult {
  /** The ids to link: everything already on the server plus everything that uploaded just now. */
  fileIds: string[]
  /** The names that could not be uploaded. Empty when everything went through. */
  failed: string[]
  /** The names the server recognised instead of storing again (#75, #234). */
  reused: string[]
}

/** Sends a staged list and reports what happened to each part. Provided by `features/files`. */
export type CommitStagedFiles = (staged: StagedFile[]) => Promise<CommitResult>
