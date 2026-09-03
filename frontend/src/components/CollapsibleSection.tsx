import { ChevronDown } from 'lucide-react'
import { useId, useState, type ReactNode } from 'react'

import { cn } from '@/lib/utils'

interface CollapsibleSectionProps {
  /** The section's title, already passed through `t()`. */
  title: string
  /** The line that sums up the collapsed content, so it does not have to be opened just to find out what is in it. */
  summary?: ReactNode
  /** The header actions. They do not open or close: they sit next to the control, not inside it. */
  actions?: ReactNode
  /** Whether it starts open. The section remembers its state while mounted, and no longer. */
  defaultOpen?: boolean
  children: ReactNode
}

/**
 * A collapsible block with a title, a summary and actions in its header
 * (`frontend-diseno.md` §5) — the pattern the legacy repeated in `CardComponent` and `ListComponent`.
 *
 * **The collapse control is a button and the actions live outside it.** Nesting a button inside
 * another is invalid HTML and, worse, makes it unpredictable what a tap does: somebody cancelling a
 * session does not also want to close the card.
 *
 * The summary exists so that a collapsed section still tells you something. A section that says
 * nothing when closed forces you to open all of them, which is exactly what collapsing was meant to
 * avoid.
 *
 * @param props.title       the title
 * @param props.summary     what to show in the header while collapsed
 * @param props.actions     the header actions
 * @param props.defaultOpen whether it starts open
 * @param props.children    the collapsible content
 */
export function CollapsibleSection({ title, summary, actions, defaultOpen = false, children }: CollapsibleSectionProps) {
  const [open, setOpen] = useState(defaultOpen)
  const contentId = useId()

  return (
    <section className="border-border rounded-lg border">
      <div className="flex items-center gap-2 px-3 py-2">
        <button
          type="button"
          onClick={() => setOpen((current) => !current)}
          aria-expanded={open}
          aria-controls={contentId}
          className="flex min-w-0 flex-1 items-center gap-2 text-left"
        >
          <ChevronDown aria-hidden="true" className={cn('size-4 shrink-0 transition-transform', open ? 'rotate-0' : '-rotate-90')} />
          <span className="truncate text-sm font-medium">{title}</span>
          {summary && <span className="text-fg-muted truncate text-xs">{summary}</span>}
        </button>
        {actions && <div className="flex shrink-0 items-center gap-1">{actions}</div>}
      </div>
      {open && (
        <div id={contentId} className="border-border border-t px-3 py-3">
          {children}
        </div>
      )}
    </section>
  )
}
