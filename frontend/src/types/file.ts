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
