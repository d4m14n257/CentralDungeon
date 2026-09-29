import { ArrowRightLeft, Ban, Check, Crown, Ellipsis, FolderInput, Merge, RotateCcw, Split, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useConfirm } from '@/hooks/useConfirm'
import { useDisclosure } from '@/hooks/useDisclosure'

import { useAcceptCatalogValue } from '../api/useAcceptCatalogValue'
import { usePromoteCatalogValue } from '../api/usePromoteCatalogValue'
import { useRejectCatalogValue } from '../api/useRejectCatalogValue'
import { useRestoreCatalogValue } from '../api/useRestoreCatalogValue'
import { useSplitCatalogGroup } from '../api/useSplitCatalogGroup'
import type { AdminCatalogValue, CatalogKind } from '../types'
import { AcceptCatalogValueDialog } from './AcceptCatalogValueDialog'
import { DisableCatalogValueDialog } from './DisableCatalogValueDialog'
import { MergeCatalogGroupsDialog } from './MergeCatalogGroupsDialog'
import { ReassignCatalogValueDialog } from './ReassignCatalogValueDialog'

/** What the menu needs. */
export interface CatalogValueActionsProps {
  /** Which catalog the value belongs to. */
  kind: CatalogKind
  /** The value the menu acts on. */
  value: AdminCatalogValue
  /**
   * Called with the id of a value that has just become a group of its own - accepted as new, or
   * split out - so the canvas can draw it as a star instead of letting it vanish from the screen.
   */
  onBecameGroup?: (id: string) => void
}

/**
 * Everything that can be done to one value of the catalog canvas (#275), as a menu.
 *
 * **It is the keyboard's way to do what the canvas does by dragging**, and it is why the canvas is
 * usable without a mouse: accepting into a group, moving to another and merging are gestures on the
 * canvas and entries here, each opening the dialog that already did it from the table. The rest -
 * promote, split, disable, restore, reject - are not gestures at all and live only here.
 *
 * What a value's state would make the server refuse is not in the menu (principio 2):
 *
 * - **pending** - accept as a group of its own, accept into a group, reject
 * - **accepted head** - merge into another group, disable
 * - **accepted alias** - make it the head, move it, split it out, disable
 * - **disabled** - restore
 *
 * @param props.kind          which catalog
 * @param props.value         the value it acts on
 * @param props.onBecameGroup called when the value becomes a group of its own
 */
export function CatalogValueActions({ kind, value, onBecameGroup }: CatalogValueActionsProps) {
  const { t } = useTranslation('catalogs')
  const confirm = useConfirm()
  const accept = useAcceptCatalogValue(kind)
  const reject = useRejectCatalogValue(kind)
  const promote = usePromoteCatalogValue(kind)
  const split = useSplitCatalogGroup(kind)
  const restore = useRestoreCatalogValue(kind)
  const acceptDialog = useDisclosure()
  const mergeDialog = useDisclosure()
  const reassignDialog = useDisclosure()
  const disableDialog = useDisclosure()

  const isPending = value.status === 'Created'
  const isAccepted = value.status === 'Accepted'
  const isDisabled = value.status === 'Disabled'
  const isHead = value.canonicalId === null

  function handleAcceptAsGroup() {
    accept.mutate(
      { id: value.id, canonicalId: null },
      {
        onSuccess: () => {
          toast.success(t('admin.acceptSuccess', { name: value.name }))
          onBecameGroup?.(value.id)
        },
      },
    )
  }

  async function handleReject() {
    const confirmed = await confirm({
      title: t('admin.rejectConfirmTitle', { name: value.name }),
      description: t('admin.rejectConfirmDescription'),
    })
    if (!confirmed) return
    reject.mutate(value.id, { onSuccess: () => toast.success(t('admin.rejectSuccess', { name: value.name })) })
  }

  async function handlePromote() {
    const confirmed = await confirm({
      title: t('admin.promoteConfirmTitle', { name: value.name }),
      description: t('admin.promoteConfirmDescription', { name: value.name, head: value.canonicalName ?? '' }),
    })
    if (!confirmed) return
    promote.mutate(value.id, { onSuccess: () => toast.success(t('admin.promoteSuccess', { name: value.name })) })
  }

  async function handleSplit() {
    const confirmed = await confirm({
      title: t('admin.splitConfirmTitle', { name: value.name }),
      description: t('admin.splitConfirmDescription'),
    })
    if (!confirmed) return
    split.mutate(value.id, {
      onSuccess: () => {
        toast.success(t('admin.splitSuccess', { name: value.name }))
        onBecameGroup?.(value.id)
      },
    })
  }

  // A rejected value never reaches the canvas, and a disabled or pending head has nothing to offer
  // but what is listed: nothing left means no menu at all, rather than an empty one.
  const hasActions = isPending || isAccepted || isDisabled
  if (!hasActions) return null

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon" aria-label={t('admin.valueActions', { name: value.name })}>
            <Ellipsis className="size-4" aria-hidden="true" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {isPending && (
            <>
              <DropdownMenuItem onSelect={handleAcceptAsGroup} disabled={accept.isPending}>
                <Check className="size-4" aria-hidden="true" />
                {t('admin.acceptAsGroup')}
              </DropdownMenuItem>
              <DropdownMenuItem onSelect={() => acceptDialog.open()}>
                <FolderInput className="size-4" aria-hidden="true" />
                {t('admin.acceptIntoGroup')}
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem variant="destructive" onSelect={() => void handleReject()} disabled={reject.isPending}>
                <X className="size-4" aria-hidden="true" />
                {t('admin.reject')}
              </DropdownMenuItem>
            </>
          )}
          {isAccepted && isHead && (
            <DropdownMenuItem onSelect={() => mergeDialog.open()}>
              <Merge className="size-4" aria-hidden="true" />
              {t('admin.merge')}
            </DropdownMenuItem>
          )}
          {isAccepted && !isHead && (
            <>
              <DropdownMenuItem onSelect={() => void handlePromote()} disabled={promote.isPending}>
                <Crown className="size-4" aria-hidden="true" />
                {t('admin.promote')}
              </DropdownMenuItem>
              <DropdownMenuItem onSelect={() => reassignDialog.open()}>
                <ArrowRightLeft className="size-4" aria-hidden="true" />
                {t('admin.reassign')}
              </DropdownMenuItem>
              <DropdownMenuItem onSelect={() => void handleSplit()} disabled={split.isPending}>
                <Split className="size-4" aria-hidden="true" />
                {t('admin.split')}
              </DropdownMenuItem>
            </>
          )}
          {isAccepted && (
            <>
              <DropdownMenuSeparator />
              <DropdownMenuItem variant="destructive" onSelect={() => disableDialog.open()}>
                <Ban className="size-4" aria-hidden="true" />
                {t('admin.disable')}
              </DropdownMenuItem>
            </>
          )}
          {isDisabled && (
            <DropdownMenuItem
              onSelect={() => restore.mutate(value.id, { onSuccess: () => toast.success(t('admin.restoreSuccess', { name: value.name })) })}
              disabled={restore.isPending}
            >
              <RotateCcw className="size-4" aria-hidden="true" />
              {t('admin.restore')}
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      {isPending && <AcceptCatalogValueDialog kind={kind} value={value} open={acceptDialog.isOpen} onOpenChange={acceptDialog.close} />}
      {isAccepted && isHead && (
        <MergeCatalogGroupsDialog kind={kind} source={value} open={mergeDialog.isOpen} onOpenChange={mergeDialog.close} />
      )}
      {isAccepted && !isHead && (
        <ReassignCatalogValueDialog kind={kind} value={value} open={reassignDialog.isOpen} onOpenChange={reassignDialog.close} />
      )}
      {isAccepted && <DisableCatalogValueDialog kind={kind} value={value} open={disableDialog.isOpen} onOpenChange={disableDialog.close} />}
    </>
  )
}
