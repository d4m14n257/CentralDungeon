import { XIcon } from 'lucide-react'
import type { ComponentProps } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'
import { DialogClose, DialogContent as BaseDialogContent } from '@/components/ui/dialog'

/**
 * The dialog panel every screen uses, with a close button that speaks the reader's language.
 *
 * The generated `ui/dialog` hard-codes its close button's accessible name in English, and that file is
 * not edited by hand. This wrapper turns that button off and draws the same one with a translated label,
 * so a screen reader on a Spanish interface does not announce "Close".
 */
export function DialogContent({ children, className, ...props }: Omit<ComponentProps<typeof BaseDialogContent>, 'showCloseButton'>) {
  const { t } = useTranslation('common')

  return (
    <BaseDialogContent showCloseButton={false} {...props} className={cn('dialog-panel', className)}>
      {children}
      <DialogClose className="ring-offset-background focus:ring-ring data-[state=open]:bg-accent data-[state=open]:text-muted-foreground absolute top-4 right-4 rounded-xs opacity-70 transition-opacity hover:opacity-100 focus:ring-2 focus:ring-offset-2 focus:outline-hidden disabled:pointer-events-none [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4">
        <XIcon />
        <span className="sr-only">{t('actions.close')}</span>
      </DialogClose>
    </BaseDialogContent>
  )
}
