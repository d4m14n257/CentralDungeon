import { useCallback, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ConfirmContext, type ConfirmFn, type ConfirmOptions } from '@/hooks/useConfirm'

interface PendingConfirm {
  options: ConfirmOptions
  resolve: (value: boolean) => void
}

/**
 * The one dialog every irreversible action goes through, never a generic "are you sure?"
 * (frontend-diseno.md principio 3).
 *
 * **Only components are exported from here**; `useConfirm` and its Context live in `hooks/`, which is
 * where a hook belongs (arquitectura.md §3.1) and which is also what keeps fast refresh working — a
 * module exporting a component *and* a hook loses it.
 */
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
