import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'

import { useChangeLibraryCategory } from '../api/useChangeLibraryCategory'
import type { AdminFile, FileCategory } from '../types'
import { PublishCategorySelect } from './PublishCategorySelect'
import { useConfirm } from '@/hooks/useConfirm'

/** What the dialog needs. */
export interface EditLibraryCategoryDialogProps {
  /** The file whose cajón is being changed, or null while the dialog is closed. */
  file: AdminFile | null
  /** Called to close it. */
  onOpenChange: (open: boolean) => void
}

/**
 * Changing what an unpublished file of the platform's library is (#282).
 *
 * **Only while nobody is offered it**: /admin/files opens this for a file that waits unpublished or
 * is hidden, never for a published one — masters are choosing that one under the cajón it has, and
 * the server refuses too. The same select as the upload list, one cajón per file.
 *
 * @param props.file         the file, or null while closed
 * @param props.onOpenChange called to close the dialog
 */
export function EditLibraryCategoryDialog({ file, onOpenChange }: EditLibraryCategoryDialogProps) {
  const { t } = useTranslation('files')
  const confirm = useConfirm()
  const change = useChangeLibraryCategory()
  // Keyed by the file, so opening it on another row starts from that row's cajón.
  const [chosen, setChosen] = useState<{ fileId: string; category: FileCategory } | null>(null)
  const current = (file?.categories[0] as FileCategory | undefined) ?? null
  const value = chosen !== null && chosen.fileId === file?.id ? chosen.category : current

  async function save() {
    if (!file || value === null) return
    // A review before anything is written (#283): what is about to change, and what follows.
    const confirmed = await confirm({
      title: t('libraryEdit.confirmTitle', { name: file.name }),
      description: t('libraryEdit.confirmDescription', { category: t(`category.${value}`) }),
      confirmLabel: t('libraryEdit.confirm'),
    })
    if (!confirmed) return
    change.mutate(
      { fileId: file.id, input: { category: value } },
      {
        onSuccess: () => {
          toast.success(t('libraryEdit.saved'))
          onOpenChange(false)
        },
      },
    )
  }

  return (
    <FormDialog
      isDirty={value !== current}
      open={file !== null}
      onOpenChange={onOpenChange}
      title={t('libraryEdit.title')}
      description={t('libraryEdit.description', { name: file?.name ?? '' })}
    >
      <div className="space-y-4">
        <div className="space-y-2">
          <Label>{t('libraryEdit.categoryLabel')}</Label>
          {file && (
            <PublishCategorySelect
              value={value}
              onChange={(category) => setChosen({ fileId: file.id, category })}
              fileName={file.name}
              disabled={change.isPending}
            />
          )}
        </div>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            {t('libraryEdit.cancel')}
          </Button>
          <Button type="button" disabled={value === null || value === current || change.isPending} onClick={() => void save()}>
            {t('libraryEdit.confirm')}
          </Button>
        </div>
      </div>
    </FormDialog>
  )
}
