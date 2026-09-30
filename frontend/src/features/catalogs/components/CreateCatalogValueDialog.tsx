import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

import { useCreateCatalogValue } from '../api/useCreateCatalogValue'
import type { CatalogKind } from '../types'

/** The column is 128 characters wide; the backend refuses anything longer. */
const MAX_NAME_LENGTH = 128

/** What the dialog needs. */
export interface CreateCatalogValueDialogProps {
  /** Which catalog the value goes into — the tab the admin is on. */
  kind: CatalogKind
  /** Whether the dialog is showing. */
  open: boolean
  /** Called to open or close it. */
  onOpenChange: (open: boolean) => void
}

/**
 * An admin adding a value to a catalog, the main action of /admin/catalogs (#280).
 *
 * It goes into **the catalog of the tab the admin is on**, which the title says, so there is no
 * second choice to make. The value is born accepted and heading its own group: the admin is the one
 * who reviews, so a proposal waiting for them would be a step with nobody on the other side.
 * Grouping it with a synonym is the canvas's job afterwards (#275).
 *
 * A name already taken, in any case, comes back as a conflict and the dialog stays open with what
 * was typed.
 *
 * @param props.kind         which catalog
 * @param props.open         whether the dialog is showing
 * @param props.onOpenChange called to open or close it
 */
export function CreateCatalogValueDialog({ kind, open, onOpenChange }: CreateCatalogValueDialogProps) {
  const { t } = useTranslation('catalogs')
  const create = useCreateCatalogValue(kind)
  const [name, setName] = useState('')
  const trimmed = name.trim()

  function close(next: boolean) {
    if (!next) setName('')
    onOpenChange(next)
  }

  function handleCreate() {
    create.mutate(
      { name: trimmed },
      {
        onSuccess: (created) => {
          toast.success(t('admin.createSuccess', { name: created.name }))
          close(false)
        },
      },
    )
  }

  return (
    <FormDialog
      isDirty={trimmed.length > 0}
      open={open}
      onOpenChange={close}
      title={t(`admin.createTitle.${kind}`)}
      description={t('admin.createDescription')}
    >
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault()
          if (trimmed.length > 0) handleCreate()
        }}
      >
        <div className="space-y-2">
          <Label htmlFor="create-catalog-value">{t('admin.createNameLabel')}</Label>
          <Input
            id="create-catalog-value"
            value={name}
            maxLength={MAX_NAME_LENGTH}
            autoFocus
            onChange={(event) => setName(event.target.value)}
          />
        </div>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={() => close(false)}>
            {t('admin.cancel')}
          </Button>
          <Button type="submit" disabled={trimmed.length === 0 || create.isPending}>
            {t('admin.createConfirm')}
          </Button>
        </div>
      </form>
    </FormDialog>
  )
}
