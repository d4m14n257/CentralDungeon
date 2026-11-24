import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { DialogContent } from '@/components/DialogContent'
import { QueryErrorState } from '@/components/QueryErrorState'
import { Dialog, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'

import { useFilePreview } from '../api/useFilePreview'
import type { PreviewKind } from '../previewKind'

interface FilePreviewDialogProps {
  /** The file to show. */
  fileId: string
  /** The original filename: the dialog's title and the image's alternative text. */
  name: string
  /** What to draw it as. */
  kind: PreviewKind
  /** Whether the dialog is open. The bytes are fetched only while it is, and dropped when it closes. */
  open: boolean
  /** Closes the dialog. */
  onOpenChange: (open: boolean) => void
}

/**
 * Looking at an image or a PDF without leaving the screen.
 *
 * The bytes come through the authenticated download and are shown from an object URL, which is
 * revoked when the dialog closes or the file changes. A PDF's blob is **re-typed as
 * `application/pdf`** before it is framed: whatever the server declared, the iframe is same-origin
 * with the app and must never be handed anything the browser could render as a page.
 *
 * The fetch lives in the dialog's body, which exists only while the dialog is open: closing it unmounts
 * the body, which releases the object URL and lets the blob leave the cache.
 *
 * Loading, error and success are the three states a read has here; there is no "empty" because a file
 * with no bytes is an error.
 *
 * @param props.fileId       the file to show
 * @param props.name         the original filename
 * @param props.kind         what to draw it as
 * @param props.open         whether the dialog is open
 * @param props.onOpenChange closes the dialog
 */
export function FilePreviewDialog({ fileId, name, kind, open, onOpenChange }: FilePreviewDialogProps) {
  const { t } = useTranslation('files')

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{name}</DialogTitle>
          <DialogDescription>{t('preview.description')}</DialogDescription>
        </DialogHeader>
        <PreviewBody fileId={fileId} name={name} kind={kind} />
      </DialogContent>
    </Dialog>
  )
}

/** The three states of the read — loading, failed, drawn — for the file of an open dialog. */
function PreviewBody({ fileId, name, kind }: Pick<FilePreviewDialogProps, 'fileId' | 'name' | 'kind'>) {
  const { t } = useTranslation('files')
  const preview = useFilePreview(fileId, name)
  const [url, setUrl] = useState<string | null>(null)
  const blob = preview.data?.blob

  useEffect(() => {
    if (!blob) {
      return
    }
    const objectUrl = URL.createObjectURL(kind === 'pdf' ? new Blob([blob], { type: 'application/pdf' }) : blob)
    setUrl(objectUrl)
    return () => {
      URL.revokeObjectURL(objectUrl)
      setUrl(null)
    }
  }, [blob, kind])

  if (preview.isLoadingError) {
    return <QueryErrorState error={preview.error} onRetry={() => void preview.refetch()} />
  }
  if (url === null) {
    return <Skeleton className="preview-frame" aria-label={t('preview.loading')} role="status" />
  }
  return kind === 'pdf' ? (
    <iframe src={url} title={name} className="preview-frame" />
  ) : (
    <img src={url} alt={name} className="preview-image" />
  )
}
