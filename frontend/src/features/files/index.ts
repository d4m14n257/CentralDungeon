/**
 * The public surface of `features/files` (#114).
 *
 * From outside, this is the only path: `@/features/files`, never a route inside it. What is not
 * exported here is private to the feature — the api module, the format helper and the query hooks
 * the components use among themselves.
 */
export { FilePicker } from './components/FilePicker'
export { FileList, type FileListItem } from './components/FileList'
export { FileCard } from './components/FileCard'
export { FileDropzone } from './components/FileDropzone'
export { FileTypeBadge } from './components/FileTypeBadge'
export { FileCategoryBadge } from './components/FileCategoryBadge'
export { FileCategoryFilter } from './components/FileCategoryFilter'
export { FileCategoryChoice } from './components/FileCategoryChoice'
export { StagedFileList } from './components/StagedFileList'
export { FileUsageChips } from './components/FileUsageChips'
export { PublishFileDialog } from './components/PublishFileDialog'
export { EditFileDialog } from './components/EditFileDialog'

export { useMyFiles } from './api/useMyFiles'
export { useMyCategories } from './api/useMyCategories'
export { usePublicFiles } from './api/usePublicFiles'
export { useTableFiles } from './api/useTableFiles'
export { useAdminFiles } from './api/useAdminFiles'
export { useUploadFile } from './api/useUploadFile'
export { useCommitStagedFiles } from './api/useCommitStagedFiles'
export { useUpdateFile } from './api/useUpdateFile'
export { useDeleteFile } from './api/useDeleteFile'
export { useDownloadFile } from './api/useDownloadFile'
export { useAttachTableFile } from './api/useAttachTableFile'
export { useAttachFilesToTable } from './api/useAttachFilesToTable'
export { useUpdateTableFile } from './api/useUpdateTableFile'
export { useDetachTableFile } from './api/useDetachTableFile'
export { usePublishFile } from './api/usePublishFile'
export { useUnpublishFile } from './api/useUnpublishFile'
export { useDeleteFileAsAdmin } from './api/useDeleteFileAsAdmin'

export { formatFileSize, type FormattedSize } from './format'
export { FILE_CATEGORIES, PUBLISHABLE_CATEGORIES } from './categories'
export { MAX_FILE_BYTES, ALLOWED_MIME_TYPES, rejectionOf } from './limits'
export { FILE_TYPE_CHOICES, fileCategoryChoices, myFileSearchFields, adminFileSearchFields } from './searchFields'
export { stagedKey } from '@/types/file'

export type {
  AdminFile,
  CommitResult,
  FileCategory,
  FileStatus,
  FileType,
  FileUsage,
  LinkTableFileInput,
  PublicFile,
  PublishFileInput,
  SharedFile,
  StagedFile,
  StoredFile,
  TableFile,
  TableFileType,
  UpdateFileInput,
  UpdateTableFileInput,
  UploadedFile,
  UploadFileInput,
} from './types'
