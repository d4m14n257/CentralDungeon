import type { CSSProperties } from 'react'

import { Toaster } from '@/components/ui/sonner'

/**
 * The palette every toast reads, taken from the design tokens of the `@theme` (#118, #130).
 *
 * **Why it exists**: the generated `components/ui/sonner.tsx` points Sonner at shadcn's default
 * variable names (`--popover`, `--border`, `--radius`), which this project's theme does not define -
 * it names them `--color-popover`, `--color-border-strong`, `--radius-lg`. Undefined, they left the
 * toast with no background at all: unreadable in dark, barely there in light. The generated file is not
 * edited by hand, so the mapping lives here and overrides its `style`.
 *
 * Each kind takes the state colours that already mean the same thing elsewhere: an error is a
 * canceled/rejected red, a success the open green, a warning the pending amber, information the
 * in-progress blue. Their `fg` on `bg` pairs are the ones `design/build.py` measures against WCAG AA in
 * both themes, so the toast inherits that guarantee instead of inventing colours of its own.
 */
const toastPalette = {
  '--normal-bg': 'var(--color-raised)',
  '--normal-text': 'var(--color-fg)',
  '--normal-border': 'var(--color-border-strong)',
  '--success-bg': 'var(--color-state-open-bg)',
  '--success-text': 'var(--color-state-open-fg)',
  '--success-border': 'var(--color-state-open-dot)',
  '--error-bg': 'var(--color-state-canceled-bg)',
  '--error-text': 'var(--color-state-canceled-fg)',
  '--error-border': 'var(--color-state-canceled-dot)',
  '--warning-bg': 'var(--color-state-pending-bg)',
  '--warning-text': 'var(--color-state-pending-fg)',
  '--warning-border': 'var(--color-state-pending-dot)',
  '--info-bg': 'var(--color-state-active-bg)',
  '--info-text': 'var(--color-state-active-fg)',
  '--info-border': 'var(--color-state-active-dot)',
  '--border-radius': 'var(--radius-lg)',
} as CSSProperties

/**
 * The application's single toast outlet, mounted once at the root.
 *
 * `richColors` is what makes Sonner use the per-kind palette above: without it every toast is the
 * neutral one, and an error reads exactly like a confirmation. The shadow separates the toast from
 * whatever surface it floats over, which in the dark theme is close to its own colour.
 */
export function AppToaster() {
  return <Toaster richColors style={toastPalette} toastOptions={{ classNames: { toast: 'shadow-lg' } }} />
}
