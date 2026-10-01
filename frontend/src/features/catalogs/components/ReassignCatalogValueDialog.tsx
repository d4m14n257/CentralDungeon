import { ArrowRightIcon } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'

import { useReassignCatalogValue } from '../api/useReassignCatalogValue'
import type { AdminCatalogValue, CatalogKind } from '../types'
import { CanonicalPicker } from './CanonicalPicker'
import { useConfirm } from '@/hooks/useConfirm'

/** What the dialog needs. */
export interface ReassignCatalogValueDialogProps {
  /** Which catalog the alias belongs to. */
  kind: CatalogKind
  /** The alias that moves. */
  value: AdminCatalogValue
  /** Whether the dialog is showing. */
  open: boolean
  /** Called to open or close it. */
  onOpenChange: (open: boolean) => void
}

/**
 * Moving an alias to another group, without dragging (#276).
 *
 * On the catalog canvas this is a gesture - drop the alias on the other group's head (#275) - and
 * this dialog is the same operation for whoever cannot or would rather not drag: a keyboard, a
 * screen reader, a phone. Every gesture of the canvas has one of these, or it is a capability only
 * some admins have.
 *
 * The picker offers group heads only and leaves out the group the alias is already in: both would
 * be refused, and a choice that is going to be refused is not offered (principio 2).
 *
 * @param props.kind         which catalog
 * @param props.value        the alias that moves
 * @param props.open         whether the dialog is showing
 * @param props.onOpenChange called to open or close it
 */
export function ReassignCatalogValueDialog({ kind, value, open, onOpenChange }: ReassignCatalogValueDialogProps) {
  const { t } = useTranslation('catalogs')
  const confirm = useConfirm()
  const reassign = useReassignCatalogValue(kind)
  const [target, setTarget] = useState<AdminCatalogValue | null>(null)

  async function handleReassign() {
    if (!target) return
    // A review before anything is written (#283): what is about to change, and what follows.
    const confirmed = await confirm({
      title: t('admin.reassignConfirmTitle', { name: value.name, target: target.name }),
      description: t('admin.reassignConfirmDescription'),
      confirmLabel: t('admin.reassign'),
    })
    if (!confirmed) return
    reassign.mutate(
      { id: value.id, canonicalId: target.id },
      {
        onSuccess: () => {
          toast.success(t('admin.reassignSuccess', { name: value.name, target: target.name }))
          onOpenChange(false)
        },
      },
    )
  }

  return (
    <FormDialog
      isDirty={target !== null}
      open={open}
      onOpenChange={onOpenChange}
      title={t('admin.reassignDialogTitle', { name: value.name })}
      description={t('admin.reassignDialogDescription')}
    >
      <div className="space-y-4">
        <div className="text-fg-muted flex flex-wrap items-center gap-2 text-sm">
          <span className="text-fg font-medium">{value.name}</span>
          <ArrowRightIcon className="size-4" aria-hidden="true" />
          <span className={target ? 'text-fg font-medium' : undefined}>{target?.name ?? t('admin.reassignPickTarget')}</span>
        </div>
        <CanonicalPicker
          kind={kind}
          selectedId={target?.id ?? null}
          onSelect={setTarget}
          {...(value.canonicalId ? { excludeId: value.canonicalId } : {})}
        />
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('admin.cancel')}
          </Button>
          <Button onClick={() => void handleReassign()} disabled={!target || reassign.isPending}>
            {t('admin.reassign')}
          </Button>
        </div>
      </div>
    </FormDialog>
  )
}
