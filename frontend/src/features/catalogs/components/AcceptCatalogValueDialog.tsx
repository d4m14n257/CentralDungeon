import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'

import { useAcceptCatalogValue } from '../api/useAcceptCatalogValue'
import { useDefineTag } from '../api/useDefineTag'
import type { AdminCatalogValue, CatalogKind } from '../types'
import { CanonicalPicker } from './CanonicalPicker'
import { definitionErrorMessage } from './definitionErrorMessage'
import { draftOf, tagDefinitionSchema } from '../tagDefinition'
import { TagDefinitionFields } from './TagDefinitionFields'
import { useConfirm } from '@/hooks/useConfirm'

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
 * Accepting a proposed value **and classifying it in the same step**, which is what ADR-12 §3 describes:
 * the admin decides whether it is a new group of its own or another name for one that exists.
 *
 * The two decisions are one dialog on purpose. Splitting them would let a value be accepted and
 * left unclassified, and an accepted value floating outside every group is precisely what
 * fragments the catalog the equivalence exists to hold together.
 *
 * A tag accepted as a group of its own can be given its category and definition here too, since
 * that is the moment it becomes a group. They are saved right after accepting, as a second request:
 * if that one fails the tag stays accepted, and the message says only the definition is missing.
 *
 * @param props.kind         which catalog
 * @param props.value        the proposal being accepted
 * @param props.open         whether the dialog is showing
 * @param props.onOpenChange called to open or close it
 */
export function AcceptCatalogValueDialog({ kind, value, open, onOpenChange }: AcceptCatalogValueDialogProps) {
  const { t } = useTranslation('catalogs')
  const confirm = useConfirm()
  const accept = useAcceptCatalogValue(kind)
  const define = useDefineTag()
  const [canonicalId, setCanonicalId] = useState<string | null>(null)
  const [draft, setDraft] = useState(() => draftOf())
  const definesGroup = kind === 'tags' && canonicalId === null
  const parsed = tagDefinitionSchema.safeParse(draft)
  const hasDefinition = definesGroup && parsed.success && (parsed.data.category !== null || parsed.data.description !== null)

  async function handleAccept() {
    // A review before anything is written (ADR-21 §9): what is about to change, and what follows.
    const confirmed = await confirm({
      title: t('admin.acceptConfirmTitle', { name: value.name }),
      description: t('admin.acceptConfirmDescription'),
      confirmLabel: t('admin.accept'),
    })
    if (!confirmed) return
    // Awaited rather than chained through `mutate` callbacks: accepting re-renders the row this dialog
    // hangs from, and a callback of an unmounted component never fires - the definition's failure would go unsaid.
    try {
      await accept.mutateAsync({ id: value.id, canonicalId })
    } catch {
      return // The global handler already said why.
    }
    toast.success(t('admin.acceptSuccess', { name: value.name }))
    if (hasDefinition && parsed.success) {
      try {
        await define.mutateAsync({ id: value.id, ...parsed.data })
      } catch (error) {
        toast.error(t('definition.acceptedWithoutDefinition', { name: value.name, reason: definitionErrorMessage(error, t) }))
      }
    }
    onOpenChange(false)
  }

  return (
    <FormDialog
      isDirty={canonicalId !== null || draft.category !== null || draft.description !== ''}
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
        {definesGroup && (
          <TagDefinitionFields idPrefix="accept-definition" value={draft} onChange={setDraft} descriptionInvalid={!parsed.success} />
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('admin.cancel')}
          </Button>
          <Button onClick={() => void handleAccept()} disabled={accept.isPending || define.isPending || (definesGroup && !parsed.success)}>
            {t('admin.accept')}
          </Button>
        </div>
      </div>
    </FormDialog>
  )
}
