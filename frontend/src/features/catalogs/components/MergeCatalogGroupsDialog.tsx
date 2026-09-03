import { ArrowRightIcon } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'

import { useMergeCatalogGroups } from '../api/useMergeCatalogGroups'
import type { AdminCatalogValue, CatalogKind } from '../types'
import { CanonicalPicker } from './CanonicalPicker'

/** What the dialog needs. */
export interface MergeCatalogGroupsDialogProps {
  /** Which catalog the groups belong to. */
  kind: CatalogKind
  /** The group that stops being one. It is the row the admin started from. */
  source: AdminCatalogValue
  /** Whether the dialog is showing. */
  open: boolean
  /** Called to open or close it. */
  onOpenChange: (open: boolean) => void
}

/**
 * Merging two synonym groups: "DANDD" and "D&D 5e" turn out to be the same thing.
 *
 * The direction matters and the dialog says so out loud - the source stops being a group, aliases
 * and all, and the target keeps its name. It is not symmetric, and an admin who reads it backwards
 * merges the wrong way round.
 *
 * What it does **not** do is touch a single row of the bridge tables (#56). A table tagged "DANDD"
 * becomes findable by "D&D 5e" the moment this runs, with nothing migrated - which is the whole
 * reason the model stores the alias the master chose rather than a normalized value.
 *
 * @param props.kind         which catalog
 * @param props.source       the group that stops being one
 * @param props.open         whether the dialog is showing
 * @param props.onOpenChange called to open or close it
 */
export function MergeCatalogGroupsDialog({ kind, source, open, onOpenChange }: MergeCatalogGroupsDialogProps) {
  const { t } = useTranslation('catalogs')
  const merge = useMergeCatalogGroups(kind)
  const [target, setTarget] = useState<AdminCatalogValue | null>(null)

  function handleMerge() {
    if (!target) return
    merge.mutate(
      { sourceCanonicalId: source.id, targetCanonicalId: target.id },
      {
        onSuccess: () => {
          toast.success(t('admin.mergeSuccess', { source: source.name, target: target.name }))
          onOpenChange(false)
        },
      },
    )
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('admin.mergeDialogTitle', { name: source.name })}
      description={t('admin.mergeDialogDescription')}
    >
      <div className="space-y-4">
        <div className="text-fg-muted flex flex-wrap items-center gap-2 text-sm">
          <span className="text-fg font-medium">{source.name}</span>
          <ArrowRightIcon className="size-4" aria-hidden="true" />
          <span className={target ? 'text-fg font-medium' : undefined}>{target?.name ?? t('admin.mergePickTarget')}</span>
        </div>
        <CanonicalPicker kind={kind} selectedId={target?.id ?? null} onSelect={setTarget} excludeId={source.id} />
        <p className="text-fg-muted text-sm">{t('admin.mergeHint', { source: source.name })}</p>
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('admin.cancel')}
          </Button>
          <Button onClick={handleMerge} disabled={!target || merge.isPending}>
            {t('admin.merge')}
          </Button>
        </div>
      </div>
    </FormDialog>
  )
}
