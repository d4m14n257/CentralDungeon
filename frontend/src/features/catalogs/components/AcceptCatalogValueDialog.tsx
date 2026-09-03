import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'

import { useAcceptCatalogValue } from '../api/useAcceptCatalogValue'
import type { AdminCatalogValue, CatalogKind } from '../types'
import { CanonicalPicker } from './CanonicalPicker'

/** What the dialog needs. */
export interface AcceptCatalogValueDialogProps {
  /** Which catalog the value belongs to. */
  kind: CatalogKind
  /** The value being accepted. */
  value: AdminCatalogValue
  /** Whether the dialog is showing. */
  open: boolean
  /** Called to open or close it. */
  onOpenChange: (open: boolean) => void
}

/**
 * Accepting a proposed value **and classifying it in the same step**, which is what #55 describes:
 * the admin decides whether it is a new group of its own or another name for one that exists.
 *
 * The two decisions are one dialog on purpose. Splitting them would let a value be accepted and
 * left unclassified, and an accepted value floating outside every group is precisely what
 * fragments the catalog the equivalence exists to hold together.
 *
 * @param props.kind         which catalog
 * @param props.value        the proposal being accepted
 * @param props.open         whether the dialog is showing
 * @param props.onOpenChange called to open or close it
 */
export function AcceptCatalogValueDialog({ kind, value, open, onOpenChange }: AcceptCatalogValueDialogProps) {
  const { t } = useTranslation('catalogs')
  const accept = useAcceptCatalogValue(kind)
  const [canonicalId, setCanonicalId] = useState<string | null>(null)

  function handleAccept() {
    accept.mutate(
      { id: value.id, canonicalId },
      {
        onSuccess: () => {
          toast.success(t('admin.acceptSuccess', { name: value.name }))
          onOpenChange(false)
        },
      },
    )
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('admin.acceptDialogTitle', { name: value.name })}
      description={t('admin.acceptDialogDescription')}
    >
      <div className="space-y-4">
        <CanonicalPicker
          kind={kind}
          selectedId={canonicalId}
          onSelect={(group) => setCanonicalId(group?.id ?? null)}
          excludeId={value.id}
          noneLabel={t('admin.acceptAsNewGroup')}
        />
        <p className="text-fg-muted text-sm">
          {canonicalId === null ? t('admin.acceptAsNewGroupHint') : t('admin.acceptAsAliasHint', { name: value.name })}
        </p>
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('admin.cancel')}
          </Button>
          <Button onClick={handleAccept} disabled={accept.isPending}>
            {t('admin.accept')}
          </Button>
        </div>
      </div>
    </FormDialog>
  )
}
