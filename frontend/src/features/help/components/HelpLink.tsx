import { useState } from 'react'

import { cn } from '@/lib/utils'

import type { HelpSectionId } from '../sections/registry'
import { HelpDialog } from './HelpDialog'

/** Props of {@link HelpLink}. */
export interface HelpLinkProps {
  /** Which piece of help to open. */
  section: HelpSectionId
  /** What the trigger says, already translated. */
  children: string
  /** Extra classes for the trigger, so a caller can size it to the text around it. */
  className?: string
}

/**
 * The way a screen offers help: opens {@link HelpDialog} in place instead of navigating (#231).
 *
 * **A `button` and not an anchor**, and `type="button"` on purpose: most of these sit inside a form,
 * and a bare `<button>` inside one submits it. The old version was a `<Link>`, which is exactly the
 * thing being removed - following it meant leaving, and leaving a wizard meant losing it.
 *
 * It looks like the link it replaces. Underlined and muted is what the reader already learned to
 * recognise as "there is an explanation here", and changing the affordance at the same time as the
 * behaviour would make the change harder to read, not easier.
 */
export function HelpLink({ section, children, className }: HelpLinkProps) {
  const [open, setOpen] = useState(false)

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={cn('text-fg-muted hover:text-fg underline underline-offset-2', className)}
      >
        {children}
      </button>
      <HelpDialog section={section} open={open} onOpenChange={setOpen} />
    </>
  )
}
