import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { useConfirm } from '@/hooks/useConfirm'
import { DialogContent } from '@/components/DialogContent'
import { Dialog, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'

interface FormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  /**
   * Whether the form inside has unsaved changes. When it has, dismissing asks first (ADR-20 §5).
   *
   * Optional because a dialog that only confirms something has nothing to lose. Anything holding
   * typed input passes it, and passing it from the form's own dirty state rather than a flag of its
   * own is what keeps the answer true.
   */
  isDirty?: boolean
  children: ReactNode
}

/**
 * A dialog holding a form. **It knows nothing about any domain**: it takes children and nothing else
 * (arquitectura §3.3, ADR-19 §6).
 *
 * **A tall form scrolls inside the dialog rather than off the screen.** `DialogContent` centres
 * itself with `top-50% translate-y-[-50%]` and caps neither its height nor its overflow, so a form
 * taller than the viewport spills equally above and below it — and the part above cannot be reached
 * by scrolling, because the page behind is what scrolls. F1.5's answer dialog, with an editor and a
 * file picker in it, is the first form long enough to hit that; the cap and the scroll container
 * here fix it for every form dialog at once.
 *
 * **Dismissing a dirty form asks first** (ADR-20 §5). Escape, the overlay and the close button all arrive
 * here as one `onOpenChange(false)`, so intercepting it once covers every way out of the dialog -
 * which is why the guard lives here and not in each of the twelve forms that use it.
 *
 * @param props.open         whether the dialog is showing
 * @param props.onOpenChange called when it is dismissed
 * @param props.title        the heading
 * @param props.description  the line under it
 * @param props.isDirty      whether the form inside holds unsaved changes
 * @param props.children     the form
 */
export function FormDialog({ open, onOpenChange, title, description, isDirty = false, children }: FormDialogProps) {
  const { t } = useTranslation('common')
  const confirm = useConfirm()

  async function requestChange(next: boolean) {
    // Opening never asks, and neither does closing a form nobody touched.
    if (next || !isDirty) {
      onOpenChange(next)
      return
    }
    const discard = await confirm({
      title: t('unsavedChanges.title'),
      description: t('unsavedChanges.description'),
      confirmLabel: t('unsavedChanges.discard'),
      cancelLabel: t('unsavedChanges.stay'),
    })
    if (discard) {
      onOpenChange(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => void requestChange(next)}>
      <DialogContent className="dialog-panel-scroll">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        <div className="overflow-y-auto">{children}</div>
      </DialogContent>
    </Dialog>
  )
}
