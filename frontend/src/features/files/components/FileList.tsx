import { DownloadIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { IconAction } from '@/components/IconAction'

import { useDownloadFile } from '../api/useDownloadFile'
import { formatFileSize } from '../format'

/**
 * The least a row needs to be listed and downloaded.
 *
 * Deliberately not one of the feature's response types: `SharedFile`, `TableFile` and the picker's
 * own rows are three different shapes that all reduce to this, and asking for the smallest of them is
 * what lets one list serve the master's tab, the player's read-only view and the picker.
 */
export interface FileListItem {
  fileId: string
  name: string
  mimeType: string
  /** The size as uploaded, before compression (#75) — the number the person recognises. */
  sizeBytes: number
}

interface FileListProps<T extends FileListItem> {
  /** The rows to show. */
  files: T[]
  /** Optional extra content under a row's name — badges, who shares it, when it was attached. */
  renderMeta?: (file: T) => ReactNode
  /** Optional per-row actions, shown after the download button. */
  renderActions?: (file: T) => ReactNode
}

/**
 * A list of files, each with its size and a way to get it.
 *
 * **Download is always here and never left to the caller.** It is the only thing every reader of
 * every one of these lists wants to do, and it is the one action that needs a fetch with the bearer
 * token rather than a link (see `useDownloadFile`) — putting it in each caller would mean each
 * caller reimplementing that.
 *
 * One column, always: a file list is read down, and it appears inside tabs and cards where a table
 * would have to become a card list on a phone anyway (§5.b).
 *
 * @template T the row shape, anything that reduces to {@link FileListItem}
 * @param props.files         the rows to show
 * @param props.renderMeta    optional extra content under a row's name
 * @param props.renderActions optional per-row actions
 */
export function FileList<T extends FileListItem>({ files, renderMeta, renderActions }: FileListProps<T>) {
  const { t, i18n } = useTranslation('files')
  const download = useDownloadFile()
  // Which row is fetching, so one download does not disable every other row's button: the mutation
  // is shared by the whole list, and `isPending` alone would say "all of them are busy".
  const busyFileId = download.isPending ? download.variables?.fileId : undefined

  return (
    <ul className="divide-border divide-y">
      {files.map((file) => {
        const size = formatFileSize(file.sizeBytes, i18n.language)
        return (
          <li key={file.fileId} className="flex items-start justify-between gap-3 py-3">
            <div className="min-w-0 space-y-1">
              <p className="text-fg truncate text-sm font-medium">{file.name}</p>
              <p className="text-fg-muted text-xs">{t(`size.${size.unit}`, { value: size.value })}</p>
              {renderMeta?.(file)}
            </div>
            <div className="flex shrink-0 items-center gap-1">
              <IconAction
                label={t('actions.download')}
                icon={<DownloadIcon className="size-4" />}
                disabled={busyFileId === file.fileId}
                onClick={() => download.mutate({ fileId: file.fileId, filename: file.name })}
              />
              {renderActions?.(file)}
            </div>
          </li>
        )
      })}
    </ul>
  )
}
