import { XIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { IconAction } from '@/components/IconAction'

import type { StagedFile } from '../types'

interface StagedFileListProps {
  /** What is waiting to be sent, in the order it was picked. */
  files: StagedFile[]
  /** Called with the entry to drop. Keyed by `localId` for new files and `fileId` for existing ones. */
  onRemove: (key: string) => void
}

/**
 * What is about to be sent, before anything is sent (#238).
 *
 * **It is the only feedback a pick gets now**, and that is the point of deferring the upload: until
 * the operation is confirmed nothing has reached the server, so the list is what tells somebody the
 * file was taken. Without it, picking a file would look like it did nothing.
 *
 * It does not distinguish a fresh upload from something reused. To whoever is assembling the set the
 * two are the same decision (#65) — the difference is only what the confirm has to do about each,
 * and that is `useCommitStagedFiles`'s business.
 *
 * @param props.files    what is waiting to be sent
 * @param props.onRemove called with the key of the entry to drop
 */
export function StagedFileList({ files, onRemove }: StagedFileListProps) {
  const { t } = useTranslation('files')

  if (files.length === 0) {
    return null
  }

  return (
    <ul className="divide-border divide-y rounded-lg border">
      {files.map((staged) => {
        const key = staged.kind === 'new' ? staged.localId : staged.fileId
        return (
          <li key={key} className="flex items-center gap-3 px-3 py-2">
            <span className="min-w-0 flex-1 truncate text-sm">{staged.name}</span>
            <IconAction
              icon={<XIcon className="size-4" />}
              label={t('staged.remove', { name: staged.name })}
              onClick={() => onRemove(key)}
            />
          </li>
        )
      })}
    </ul>
  )
}
