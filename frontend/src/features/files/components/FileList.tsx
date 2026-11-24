import { DownloadIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { IconAction } from '@/components/IconAction'

import { useDownloadFile } from '../api/useDownloadFile'
import { FileCard } from './FileCard'
import { FilePreviewButton } from './FilePreviewButton'

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
  /** The size as uploaded, before compression (ADR-13 §3) — the number the person recognises. */
  sizeBytes: number
}

interface FileListProps<T extends FileListItem> {
  /** The rows to show. */
  files: T[]
  /** Optional extra content under a row's name — badges, who shares it, when it was attached. */
  renderMeta?: (file: T) => ReactNode
  /** Optional per-row actions, shown after the download button. */
  renderActions?: (file: T) => ReactNode
  /** Whether a row is marked for a batch action: it is drawn marked, in words as well as in fill. */
  isMarked?: (file: T) => boolean
}

/**
 * A list of files, each with its size and a way to get it.
 *
 * **Preview and download are always here and never left to the caller.** «View» appears only for images
 * and PDFs, each opening in a dialog; everything else is download-only.
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
 * @param props.isMarked      optional: whether a row is marked for a batch action
 */
export function FileList<T extends FileListItem>({ files, renderMeta, renderActions, isMarked }: FileListProps<T>) {
  const { t } = useTranslation('files')
  const download = useDownloadFile()
  // Which row is fetching, so one download does not disable every other row's button: the mutation
  // is shared by the whole list, and `isPending` alone would say "all of them are busy".
  const busyFileId = download.isPending ? download.variables?.fileId : undefined

  return (
    <ul className="list-divided-bare">
      {files.map((file) => (
        <li key={file.fileId}>
          <FileCard
            name={file.name}
            mimeType={file.mimeType}
            sizeBytes={file.sizeBytes}
            meta={renderMeta?.(file)}
            marked={isMarked?.(file) ?? false}
            actions={
              <>
                <FilePreviewButton fileId={file.fileId} name={file.name} mimeType={file.mimeType} />
                <IconAction
                  label={t('actions.download')}
                  icon={<DownloadIcon className="size-4" />}
                  disabled={busyFileId === file.fileId}
                  onClick={() => download.mutate({ fileId: file.fileId, filename: file.name })}
                />
                {renderActions?.(file)}
              </>
            }
          />
        </li>
      ))}
    </ul>
  )
}
