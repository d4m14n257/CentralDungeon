import type { ReactNode } from 'react'

import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'

interface FormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  children: ReactNode
}

/**
 * A dialog holding a form. **It knows nothing about any domain**: it takes children and nothing else
 * (arquitectura.md §3.3, #110).
 *
 * **A tall form scrolls inside the dialog rather than off the screen.** `DialogContent` centres
 * itself with `top-50% translate-y-[-50%]` and caps neither its height nor its overflow, so a form
 * taller than the viewport spills equally above and below it — and the part above cannot be reached
 * by scrolling, because the page behind is what scrolls. F1.5's answer dialog, with an editor and a
 * file picker in it, is the first form long enough to hit that; the cap and the scroll container
 * here fix it for every form dialog at once.
 *
 * @param props.open         whether the dialog is showing
 * @param props.onOpenChange called when it is dismissed
 * @param props.title        the heading
 * @param props.description  the line under it
 * @param props.children     the form
 */
export function FormDialog({ open, onOpenChange, title, description, children }: FormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85svh] grid-rows-[auto_minmax(0,1fr)]">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        <div className="overflow-y-auto">{children}</div>
      </DialogContent>
    </Dialog>
  )
}
