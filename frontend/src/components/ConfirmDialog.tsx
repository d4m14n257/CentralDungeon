import { createContext, use, useCallback, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'

interface ConfirmOptions {
  title: string
  description: string
  confirmLabel?: string
  cancelLabel?: string
}

type ConfirmFn = (options: ConfirmOptions) => Promise<boolean>

const ConfirmContext = createContext<ConfirmFn | null>(null)

interface PendingConfirm {
  options: ConfirmOptions
  resolve: (value: boolean) => void
}

/** Every irreversible action goes through this, never a generic "are you sure?" (frontend-diseno.md principio 3). */
export function ConfirmDialogProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation('common')
  const [pending, setPending] = useState<PendingConfirm | null>(null)

  const confirm = useCallback<ConfirmFn>(
    (options) =>
      new Promise((resolve) => {
        setPending({ options, resolve })
      }),
    [],
  )

  function settle(value: boolean) {
    pending?.resolve(value)
    setPending(null)
  }

  return (
    <ConfirmContext value={confirm}>
      {children}
      <Dialog open={pending !== null} onOpenChange={(open) => !open && settle(false)}>
        <DialogContent>
          {pending && (
            <>
              <DialogHeader>
                <DialogTitle>{pending.options.title}</DialogTitle>
                <DialogDescription>{pending.options.description}</DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button variant="outline" onClick={() => settle(false)}>
                  {pending.options.cancelLabel ?? t('actions.cancel')}
                </Button>
                <Button onClick={() => settle(true)}>{pending.options.confirmLabel ?? t('actions.confirm')}</Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </ConfirmContext>
  )
}

/**
 * Asks for confirmation from anywhere, without each caller mounting its own dialog.
 *
 * A Context and not Zustand: the hook has to *render* something, so the state belongs to the subtree
 * that provides it (#105).
 *
 * @returns a function that opens the dialog and resolves to whether the user confirmed
 */
export function useConfirm(): ConfirmFn {
  const context = use(ConfirmContext)
  if (!context) {
    throw new Error('useConfirm must be used within ConfirmDialogProvider')
  }
  return context
}
