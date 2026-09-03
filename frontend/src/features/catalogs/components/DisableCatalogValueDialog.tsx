import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

import { useCatalogGroup } from '../api/useCatalogGroup'
import { useDisableCatalogValue } from '../api/useDisableCatalogValue'
import type { AdminCatalogValue, CatalogKind } from '../types'

/** What the dialog needs. */
export interface DisableCatalogValueDialogProps {
  /** Which catalog the value belongs to. */
  kind: CatalogKind
  /** The value being taken out of circulation. */
  value: AdminCatalogValue
  /** Whether the dialog is showing. */
  open: boolean
  /** Called to open or close it. */
  onOpenChange: (open: boolean) => void
}

/**
 * Taking a value out of circulation (#81) - and, when it is a group's canonical entry, handing the
 * group to somebody else at the same time.
 *
 * Those two are one operation rather than two, because under a flat `canonical_id` they *are* the
 * same one (#59): a group whose canonical entry went away needs a new one, and the alternative -
 * promoting whichever alias happens to come first - is a decision the system is not entitled to
 * make (#55). So the dialog reads the group, and asks only when there is something to ask.
 *
 * What it never does is break a link. Every table tagged with the value keeps its row, the value
 * stays in the group as an alias of the successor, and restoring puts all of it back with nothing
 * migrated - which is why "disable" and "delete" are different words here.
 *
 * @param props.kind         which catalog
 * @param props.value        the value being disabled
 * @param props.open         whether the dialog is showing
 * @param props.onOpenChange called to open or close it
 */
export function DisableCatalogValueDialog({ kind, value, open, onOpenChange }: DisableCatalogValueDialogProps) {
  const { t } = useTranslation('catalogs')
  const disable = useDisableCatalogValue(kind)
  const { data: group, isPending } = useCatalogGroup(kind, open ? value.id : null)
  const [successorId, setSuccessorId] = useState<string | null>(null)

  // Only a canonical entry can leave a group behind, and only live aliases need somewhere to go:
  // a disabled or still-unreviewed synonym is not what makes a successor necessary.
  const liveAliases = (group ?? []).filter((member) => member.id !== value.id && member.status === 'Accepted')
  const needsSuccessor = value.canonicalId === null && liveAliases.length > 0

  function handleDisable() {
    disable.mutate(
      { id: value.id, newCanonicalId: needsSuccessor ? successorId : null },
      {
        onSuccess: () => {
          toast.success(t('admin.disableSuccess', { name: value.name }))
          onOpenChange(false)
        },
      },
    )
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('admin.disableDialogTitle', { name: value.name })}
      description={t('admin.disableDialogDescription')}
    >
      <div className="space-y-4">
        {isPending && <Skeleton className="h-24 w-full" />}
        {!isPending && needsSuccessor && (
          <>
            <p className="text-sm">{t('admin.disableNeedsSuccessor', { count: liveAliases.length })}</p>
            <ul className="border-border divide-border divide-y rounded-md border">
              {liveAliases.map((alias) => (
                <li key={alias.id}>
                  <button
                    type="button"
                    onClick={() => setSuccessorId(alias.id)}
                    className={cn(
                      'hover:bg-accent w-full px-3 py-2 text-left text-sm',
                      successorId === alias.id && 'bg-accent font-medium',
                    )}
                  >
                    {alias.name}
                  </button>
                </li>
              ))}
            </ul>
          </>
        )}
        {!isPending && !needsSuccessor && <p className="text-fg-muted text-sm">{t('admin.disableKeepsLinks')}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('admin.cancel')}
          </Button>
          <Button
            variant="destructive"
            onClick={handleDisable}
            disabled={disable.isPending || isPending || (needsSuccessor && successorId === null)}
          >
            {t('admin.disable')}
          </Button>
        </div>
      </div>
    </FormDialog>
  )
}
