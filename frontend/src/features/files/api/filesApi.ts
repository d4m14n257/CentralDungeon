import { api, type DownloadedFile } from '@/api/client'
import { pageSize } from '@/config/pagination'

import type {
  AdminFile,
  FileCategory,
  LinkTableFileInput,
  PublicFile,
  PublishFileInput,
  StoredFile,
  TableFile,
  UpdateFileInput,
  UpdateTableFileInput,
  UploadedFile,
  UploadFileInput,
} from '../types'

/**
 * Every call about files, split the way the backend splits them: a person's own under
 * `/api/v1/files`, a table's attachments under `/api/v1/game-tables/{id}/files`, and the admin
 * operations under `/api/v1/admin/files`.
 *
 * **There is no "upload to this table" call, on purpose.** Uploading and attaching are two requests,
 * which is what makes "upload a new one" and "reuse one I already have" end in the same second call.
 * Reuse is the cost lever of the whole fase (#65, #75), so it cannot be the path with the extra step.
 */
export const filesApi = {
  /**
   * Uploads a file.
   *
   * **Reads the status, not just the body** (#234): a 201 wrote the content, a 200 means this person
   * already had it and the row they had came back instead. Both are successes and the caller gets a
   * `StoredFile` either way — the flag exists so the screen can say reuse happened, which until now
   * it had no way of knowing.
   *
   * @param file  the content the person picked
   * @param input which lifecycle it should have (#68) and what it is (#233)
   */
  upload: async (file: File, input: UploadFileInput): Promise<UploadedFile> => {
    const { data, status } = await api.uploadWithStatus<StoredFile>('/api/v1/files', [file], input)
    return { file: data, deduplicated: status === 200 }
  },

  /**
   * The reuse history of #65 — everything this person uploaded and still keeps, each with where it
   * is being used (#232).
   *
   * @param query    the search box over their own filenames, or undefined for everything
   * @param category the kind of document to narrow to (#233), or undefined for every kind
   * @param page     zero-based page number
   */
  listMine: (query?: string, category?: FileCategory, page = 0) =>
    api.getPage<StoredFile>('/api/v1/files/mine', { q: query, category, page, size: pageSize.picker }),

  /**
   * The cajones this person may file something of their own under (#237).
   *
   * The server decides, so the screen cannot offer a cajón the upload would be refused for.
   */
  listMyCategories: () => api.get<FileCategory[]>('/api/v1/files/mine/categories'),

  /**
   * What the platform published, for whoever is choosing one to attach (#64, #79).
   *
   * @param category the cajón to narrow to (#233), or undefined for everything published. It
   *                 replaced the audience of #64: a flow already says who a document is for
   */
  listPublic: (category?: FileCategory) => api.getPage<PublicFile>('/api/v1/files/public', { category, size: pageSize.picker }),

  /**
   * Fetches a file's bytes.
   *
   * @param fileId   the file
   * @param filename what to save it as if the response carries no `Content-Disposition`
   */
  download: (fileId: string, filename: string): Promise<DownloadedFile> => api.download(`/api/v1/files/${fileId}/content`, filename),

  /**
   * Renames a file and decides whether to keep it in the history (#65, #68).
   *
   * @param fileId the file
   * @param input  the name it should end up with, and whether to keep it
   */
  update: (fileId: string, input: UpdateFileInput) => api.patch<StoredFile, UpdateFileInput>(`/api/v1/files/${fileId}`, input),

  /**
   * Lets go of a file. Marks it gone; the bytes wait for the platform owner (#25, #66).
   *
   * @param fileId the file
   */
  remove: (fileId: string) => api.delete(`/api/v1/files/${fileId}`),

  /**
   * Everything attached to a table, private attachments included.
   *
   * @param tableId the table
   */
  listForTable: (tableId: string) => api.get<TableFile[]>(`/api/v1/game-tables/${tableId}/files`),

  /**
   * Attaches an existing file to a table — the actor's own, or one the platform published (#79).
   *
   * @param tableId the table
   * @param input   the file to attach and how
   */
  attach: (tableId: string, input: LinkTableFileInput) =>
    api.post<TableFile, LinkTableFileInput>(`/api/v1/game-tables/${tableId}/files`, input),

  /**
   * Changes what an attachment is for, or whether the table's players see it.
   *
   * @param tableId the table
   * @param fileId  the attached file
   * @param input   what it should be for, and whether it stays with the masters
   */
  updateAttachment: (tableId: string, fileId: string, input: UpdateTableFileInput) =>
    api.patch<TableFile, UpdateTableFileInput>(`/api/v1/game-tables/${tableId}/files/${fileId}`, input),

  /**
   * Takes a file off a table. The file survives, everywhere else it is (#79).
   *
   * @param tableId the table
   * @param fileId  the file to take off
   */
  detach: (tableId: string, fileId: string) => api.delete(`/api/v1/game-tables/${tableId}/files/${fileId}`),

  /**
   * /admin/files: everything, searchable, with the usage count that makes #79 visible.
   *
   * @param query     the search box in the language of #164, or undefined for everything
   * @param statuses  the statuses to keep, or undefined for all of them
   * @param fileTypes  the lifecycles to keep (#68), or undefined for all of them
   * @param category   the cajón to keep (#233), or undefined for all of them
   * @param page       zero-based page number
   */
  listForAdmin: (query?: string, statuses?: string[], fileTypes?: string[], category?: FileCategory, page = 0) => {
    const params = new URLSearchParams()
    if (query) {
      params.set('q', query)
    }
    for (const status of statuses ?? []) {
      params.append('status', status)
    }
    for (const fileType of fileTypes ?? []) {
      params.append('fileType', fileType)
    }
    if (category) {
      params.set('category', category)
    }
    params.set('page', String(page))
    params.set('size', String(pageSize.adminQueue))
    return api.getPage<AdminFile>(`/api/v1/admin/files?${params.toString()}`)
  },

  /**
   * Publishes a file for the whole platform, with its audience (#64).
   *
   * @param fileId the file
   * @param input  who it is for
   */
  publish: (fileId: string, input: PublishFileInput) =>
    api.post<AdminFile, PublishFileInput>(`/api/v1/admin/files/${fileId}/publish`, input),

  /**
   * Takes a file back out of the published set. Tables that attached it keep it (#79).
   *
   * @param fileId the file
   */
  unpublish: (fileId: string) => api.post<AdminFile>(`/api/v1/admin/files/${fileId}/unpublish`),

  /**
   * An admin removing any file, including one somebody else uploaded. Still a mark (#25, #66).
   *
   * @param fileId the file
   */
  removeAsAdmin: (fileId: string) => api.delete(`/api/v1/admin/files/${fileId}`),
}
