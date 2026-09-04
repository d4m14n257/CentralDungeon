/**
 * The public surface of `features/files` (#114).
 *
 * From outside, this is the only path: `@/features/files`, never a route inside it. What is not
 * exported here is private to the feature — the api module, the format helper and the query hooks
 * the components use among themselves.
 */
export { FilePicker } from './components/FilePicker'
export { FileList, type FileListItem } from './components/FileList'
export { FileTypeBadge } from './components/FileTypeBadge'
export { FileAudienceBadge } from './components/FileAudienceBadge'
export { PublishFileDialog } from './components/PublishFileDialog'

export { useMyFiles } from './api/useMyFiles'
export { usePublicFiles } from './api/usePublicFiles'
export { useTableFiles } from './api/useTableFiles'
export { useAdminFiles } from './api/useAdminFiles'
export { useUploadFile } from './api/useUploadFile'
export { useUpdateFile } from './api/useUpdateFile'
export { useDeleteFile } from './api/useDeleteFile'
export { useDownloadFile } from './api/useDownloadFile'
export { useAttachTableFile } from './api/useAttachTableFile'
export { useUpdateTableFile } from './api/useUpdateTableFile'
export { useDetachTableFile } from './api/useDetachTableFile'
export { usePublishFile } from './api/usePublishFile'
export { useUnpublishFile } from './api/useUnpublishFile'
export { useDeleteFileAsAdmin } from './api/useDeleteFileAsAdmin'

export { formatFileSize, type FormattedSize } from './format'

export type {
  AdminFile,
  FileStatus,
  FileType,
  LinkTableFileInput,
  PublicAudience,
  PublicFile,
  PublishFileInput,
  SharedFile,
  StoredFile,
  TableFile,
  TableFileType,
  UpdateFileInput,
  UpdateTableFileInput,
  UploadFileInput,
} from './types'
