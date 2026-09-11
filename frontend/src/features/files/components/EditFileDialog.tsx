import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

import type { StoredFile, UpdateFileInput } from '../types'

interface EditFileDialogProps {
  /** The file being edited, or null when the dialog is closed. */
  file: StoredFile | null
  onOpenChange: (open: boolean) => void
  /** Called with the state the file should end in. */
  onConfirm: (input: UpdateFileInput) => void
  isPending: boolean
}

/**
 * Renaming a file and deciding whether to keep it (#65, #68).
 *
 * **The two belong together because they are the same errand.** Somebody opening this is tidying
 * their own library: the file the browser called "documento (3).pdf" gets the name it should have
 * had, and the one they only needed once stops being kept.
 *
 * **No cajón here** (#233). A membership is what the file's uses made true and is never revoked, so
 * there is nothing to correct: the only ways in are to be used in a flow, or to be put there on an
 * upload that had no flow to observe.
 *
 * **Renaming touches nothing but metadata.** The content lives under a generated key and never had
 * anything to do with what the file is called (#80), so this cannot break a link on any table.
 *
 * The whole state travels on confirm rather than a delta, the same reasoning as #189: "null means
 * leave it alone" makes clearing a value impossible to express and every caller guess.
 *
 * @param props.file         the file being edited, or null when closed
 * @param props.onOpenChange closes the dialog
 * @param props.onConfirm    called with the state the file should end in
 * @param props.isPending    true while the request is in flight
 */
export function EditFileDialog({ file, onOpenChange, onConfirm, isPending }: EditFileDialogProps) {
  const { t } = useTranslation('files')
  // Keyed by file id so opening the dialog on another row starts from *that* file's values rather
  // than the previous one's — plain `useState` would keep the stale draft.
  const [draft, setDraft] = useState<{ fileId: string; input: UpdateFileInput } | null>(null)

  const current: UpdateFileInput =
    draft !== null && draft.fileId === file?.id ? draft.input : { name: file?.name ?? '', keepInLibrary: file?.fileType !== 'SingleUse' }

  function change(patch: Partial<UpdateFileInput>) {
    if (file) {
      setDraft({ fileId: file.id, input: { ...current, ...patch } })
    }
  }

  const isDirty = current.name !== (file?.name ?? '') || current.keepInLibrary !== (file?.fileType !== 'SingleUse')

  return (
    <FormDialog
      isDirty={isDirty}
      open={file !== null}
      onOpenChange={onOpenChange}
      title={t('edit.title')}
      description={t('edit.description')}
    >
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="edit-file-name">{t('edit.nameLabel')}</Label>
          <Input id="edit-file-name" value={current.name} maxLength={256} onChange={(event) => change({ name: event.target.value })} />
        </div>

        <div className="flex items-start gap-2">
          <Checkbox
            id="edit-file-keep"
            checked={current.keepInLibrary}
            onCheckedChange={(checked) => change({ keepInLibrary: checked === true })}
          />
          <div className="space-y-1">
            <Label htmlFor="edit-file-keep">{t('edit.keepLabel')}</Label>
            <p className="text-fg-muted text-xs">{t('edit.keepHint')}</p>
          </div>
        </div>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            {t('edit.cancel')}
          </Button>
          <Button type="button" disabled={isPending || current.name.trim() === ''} onClick={() => onConfirm(current)}>
            {t('edit.confirm')}
          </Button>
        </div>
      </div>
    </FormDialog>
  )
}
