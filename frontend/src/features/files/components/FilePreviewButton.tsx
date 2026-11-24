import { FileSearchIcon } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { IconAction } from '@/components/IconAction'

import { previewKindOf } from '../previewKind'
import { FilePreviewDialog } from './FilePreviewDialog'

interface FilePreviewButtonProps {
  /** The file to preview. */
  fileId: string
  /** The original filename. */
  name: string
  /** The declared MIME type, which decides whether there is anything to preview. */
  mimeType: string
}

/**
 * The «View» action of a file row, with the dialog it opens.
 *
 * It draws nothing for a file that is neither an image nor a PDF: an action that cannot work is
 * absent, not greyed out, and a Word document stays download-only. Each row owns its dialog, which
 * costs nothing while closed and keeps every list free of preview state.
 *
 * @param props.fileId   the file to preview
 * @param props.name     the original filename
 * @param props.mimeType the declared MIME type
 */
export function FilePreviewButton({ fileId, name, mimeType }: FilePreviewButtonProps) {
  const { t } = useTranslation('files')
  const [open, setOpen] = useState(false)
  const kind = previewKindOf(mimeType, name)

  if (kind === null) {
    return null
  }

  return (
    <>
      <IconAction label={t('actions.preview')} icon={<FileSearchIcon className="size-4" />} onClick={() => setOpen(true)} />
      <FilePreviewDialog fileId={fileId} name={name} kind={kind} open={open} onOpenChange={setOpen} />
    </>
  )
}
