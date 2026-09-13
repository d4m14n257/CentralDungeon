import { useTranslation } from 'react-i18next'

import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'

import { HELP_SECTIONS, type HelpSectionBodyProps, type HelpSectionId } from '../sections/registry'

/** Props of {@link HelpDialog}. */
export interface HelpDialogProps extends HelpSectionBodyProps {
  /** Which piece of help to show. */
  section: HelpSectionId
  /** Whether the dialog is showing. */
  open: boolean
  /** Called when it is dismissed. */
  onOpenChange: (open: boolean) => void
}

/**
 * One piece of help, read without leaving the screen that raised the question (#231).
 *
 * **This is the whole point of the change**: the help used to be a route, and reading it meant
 * navigating away - which threw out a half-filled wizard. A dialog keeps the work behind it and
 * shows one section rather than a page of them, so the answer is not buried in nine others.
 *
 * It scrolls inside itself, the same way {@link FormDialog} does: some sections are long, and a
 * dialog taller than the viewport spills off both edges with only the page behind able to scroll.
 */
export function HelpDialog({ section, open, onOpenChange, ...context }: HelpDialogProps) {
  const { t } = useTranslation('help')
  const { titleKey, Body } = HELP_SECTIONS[section]

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85svh] grid-rows-[auto_minmax(0,1fr)] sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle className="font-serif">{t(titleKey)}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 overflow-y-auto">
          <Body {...context} />
        </div>
      </DialogContent>
    </Dialog>
  )
}
