import { createContext, use } from 'react'

/** What a caller asks to be confirmed. */
export interface ConfirmOptions {
  title: string
  description: string
  confirmLabel?: string
  cancelLabel?: string
}

/** Opens the confirmation and resolves to what the person answered. */
export type ConfirmFn = (options: ConfirmOptions) => Promise<boolean>

/**
 * The channel between the hook and the dialog that renders it.
 *
 * **A subtree Context and not a global store**: what is being shared is something that has to be
 * *rendered*, so the state belongs to the subtree that provides it (#105). It is declared here, with
 * the hook, so that `ConfirmDialog.tsx` exports only components — a file that exports both a
 * component and a hook is what breaks fast refresh.
 */
export const ConfirmContext = createContext<ConfirmFn | null>(null)

/**
 * Asks for confirmation from anywhere, without each caller mounting its own dialog.
 *
 * Every irreversible action goes through this, never a generic "are you sure?" — the question names
 * what is about to happen (frontend-diseno.md principio 3).
 *
 * @returns a function that opens the dialog and resolves to whether the person confirmed
 * @throws when called outside `ConfirmDialogProvider`, which is a wiring mistake and not a state
 */
export function useConfirm(): ConfirmFn {
  const context = use(ConfirmContext)
  if (!context) {
    throw new Error('useConfirm must be used within ConfirmDialogProvider')
  }
  return context
}
