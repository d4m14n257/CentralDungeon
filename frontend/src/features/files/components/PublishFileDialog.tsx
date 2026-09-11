import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'

import { PUBLISHABLE_CATEGORIES } from '../categories'
import type { AdminFile, FileCategory } from '../types'

interface PublishFileDialogProps {
  /** The file being published, or null when the dialog is closed. */
  file: AdminFile | null
  onOpenChange: (open: boolean) => void
  /** Called with the cajones the admin chose. Never empty — the button refuses. */
  onConfirm: (categories: FileCategory[]) => void
  isPending: boolean
}

/**
 * Publishing a file for the whole platform, into the cajones it is offered in (#233).
 *
 * **Checkboxes and not a select, because a file can be offered in several flows at once** — and that
 * is the case the whole redesign turns on. The community's blank sheet is asked for while a table
 * recruits *and* again once it is running, so it goes into `TableMaterial` and `MasterRequest`
 * together: one file, one blob, two rows. The column this replaced would have forced a choice and
 * quietly broken whichever flow lost.
 *
 * **Only three of the five are offered.** The two player-side cajones hold what individual people
 * answered with, and a blank offered to everybody is not an answer — it belongs in the master-side
 * cajón the request was written from. The backend refuses them too; this keeps the screen from
 * offering a choice that would come back rejected.
 *
 * At least one is required, which is M24.1's fix carried across from the audience it replaced: the
 * legacy returned every public file everywhere, so a document written for masters turned up in front
 * of a player.
 *
 * The dialog says what publishing does, because it is the least reversible thing on the screen:
 * masters start attaching the file, and unpublishing it later does not take it off their tables (#79).
 *
 * @param props.file         the file being published, or null when closed
 * @param props.onOpenChange closes the dialog
 * @param props.onConfirm    called with the chosen cajones
 * @param props.isPending    true while the request is in flight
 */
export function PublishFileDialog({ file, onOpenChange, onConfirm, isPending }: PublishFileDialogProps) {
  const { t } = useTranslation('files')
  // Keyed by file id so opening the dialog on a different row starts clean instead of carrying the
  // previous file's answer — plain `useState` would keep it.
  const [chosen, setChosen] = useState<{ fileId: string; values: FileCategory[] } | null>(null)
  const values = chosen !== null && chosen.fileId === file?.id ? chosen.values : []

  function toggle(category: FileCategory) {
    if (!file) return
    setChosen({
      fileId: file.id,
      values: values.includes(category) ? values.filter((value) => value !== category) : [...values, category],
    })
  }

  return (
    <FormDialog
      isDirty={values.length > 0}
      open={file !== null}
      onOpenChange={onOpenChange}
      title={t('publish.title')}
      description={t('publish.description', { name: file?.name ?? '' })}
    >
      <div className="space-y-4">
        <div className="space-y-2">
          <p className="text-sm font-medium">{t('publish.categoriesLabel')}</p>
          {PUBLISHABLE_CATEGORIES.map((category) => (
            <div key={category} className="flex items-start gap-2">
              <Checkbox id={`publish-${category}`} checked={values.includes(category)} onCheckedChange={() => toggle(category)} />
              <div className="space-y-0.5">
                <Label htmlFor={`publish-${category}`}>{t(`category.${category}`)}</Label>
                <p className="text-fg-muted text-xs">{t(`publish.hint.${category}`)}</p>
              </div>
            </div>
          ))}
          <p className="text-fg-muted text-xs">{t('publish.categoriesHint')}</p>
        </div>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            {t('publish.cancel')}
          </Button>
          {/* Disabled with nothing chosen: publishing into no cajón is the state M24.1 refuses. */}
          <Button type="button" disabled={isPending || values.length === 0} onClick={() => onConfirm(values)}>
            {t('publish.confirm')}
          </Button>
        </div>
      </div>
    </FormDialog>
  )
}
