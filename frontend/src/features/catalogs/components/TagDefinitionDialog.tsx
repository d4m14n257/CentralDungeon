import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { useConfirm } from '@/hooks/useConfirm'

import { useDefineTag } from '../api/useDefineTag'
import type { AdminCatalogValue } from '../types'
import { definitionErrorMessage } from './definitionErrorMessage'
import { draftOf, tagDefinitionSchema } from '../tagDefinition'
import { TagDefinitionFields } from './TagDefinitionFields'

/** What the dialog needs. */
export interface TagDefinitionDialogProps {
  /** The tag group's canonical entry. */
  value: AdminCatalogValue
  /** Whether the dialog is showing. */
  open: boolean
  /** Called to open or close it. */
  onOpenChange: (open: boolean) => void
}

/**
 * An admin saying what a tag group is: its category and its definition, which every name of the
 * group shows. Opened only on a group's canonical entry, the one row that stores them.
 *
 * A failure is shown under the form, which keeps what was typed, rather than in a toast that would
 * leave the admin guessing which of the two fields to fix.
 *
 * @param props.value        the canonical tag being defined
 * @param props.open         whether the dialog is showing
 * @param props.onOpenChange called to open or close it
 */
export function TagDefinitionDialog({ value, open, onOpenChange }: TagDefinitionDialogProps) {
  const { t } = useTranslation('catalogs')
  const confirm = useConfirm()
  const define = useDefineTag()
  const [draft, setDraft] = useState(() => draftOf(value))
  const parsed = tagDefinitionSchema.safeParse(draft)
  const initial = draftOf(value)
  const isDirty = draft.category !== initial.category || draft.description !== initial.description

  const { reset: resetDefine } = define

  // Mounted once and reused, so the group's current definition is put back each time it opens - set
  // while rendering, the moment `open` flips, rather than one render late from an effect.
  const [wasOpen, setWasOpen] = useState(open)
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) setDraft(draftOf(value))
  }

  // The failure of a previous attempt does not greet the next one.
  useEffect(() => {
    if (open) resetDefine()
  }, [open, resetDefine])

  async function handleSave() {
    if (!parsed.success) return
    const confirmed = await confirm({
      title: t('definition.confirmTitle', { name: value.name }),
      description: t('definition.confirmDescription'),
      confirmLabel: t('definition.save'),
    })
    if (!confirmed) return
    define.mutate(
      { id: value.id, ...parsed.data },
      {
        onSuccess: () => {
          toast.success(t('definition.success', { name: value.name }))
          onOpenChange(false)
        },
      },
    )
  }

  return (
    <FormDialog
      isDirty={isDirty}
      open={open}
      onOpenChange={onOpenChange}
      title={t('definition.title', { name: value.name })}
      description={t('definition.description')}
    >
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault()
          void handleSave()
        }}
      >
        <TagDefinitionFields idPrefix="tag-definition" value={draft} onChange={setDraft} descriptionInvalid={!parsed.success} />
        {define.isError && (
          <p role="alert" className="text-destructive text-sm">
            {definitionErrorMessage(define.error, t)}
          </p>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            {t('admin.cancel')}
          </Button>
          <Button type="submit" disabled={!parsed.success || define.isPending}>
            {t('definition.save')}
          </Button>
        </div>
      </form>
    </FormDialog>
  )
}
