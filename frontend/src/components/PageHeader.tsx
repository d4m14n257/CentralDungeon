import { ArrowLeft } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'

import { Button } from '@/components/ui/button'
import { HelpButton, type HelpSectionId } from '@/features/help'

/**
 * The one main action of a screen (#280). An object and not a node, so every screen's button comes
 * out the same: violet, normal size, at the right edge.
 */
export interface PageAction {
  /** What the button says, already translated. */
  label: string
  /** Where it goes, for an action that is a page of its own. Exactly one of `to` and `onClick`. */
  to?: string
  /** What it does, for an action that happens here — a dialog, a mutation. */
  onClick?: () => void
  /** True while it cannot run yet, such as while what it needs is still loading. */
  disabled?: boolean
}

/** Where «Volver» goes and what it says. */
export interface PageBack {
  /** The screen it returns to. */
  to: string
  /** What it says, already translated — «Volver a archivos», naming where it leads. */
  label: string
}

/** What the header of a screen shows. */
interface PageHeaderProps {
  /** The screen's name, already translated. Rendered as its only `h1`. */
  title: ReactNode
  /** One line under the title saying what the screen is for, already translated. */
  description?: ReactNode
  /** Something that qualifies the title itself, next to it — a table's status badge. */
  badge?: ReactNode
  /** The screen this one returns to, as a small link above the title. */
  back?: PageBack
  /** The help section that explains the screen, opened by «Cómo funciona». */
  help?: HelpSectionId
  /** The screen's one main action, at the right edge. */
  action?: PageAction | undefined
}

/**
 * The top of a screen, laid out the same way on every one of them (#273, #280).
 *
 * - **«Volver»**, when the screen has somewhere to return to, is a small link **above** the title —
 *   never a button across from it, where it looked like the screen's action.
 * - **Title and description** on the left.
 * - **On the right, always in this order**: «Cómo funciona» (`HelpButton`) and then the main action,
 *   violet and normal size, against the edge — however long the description is: the text wraps, the
 *   buttons do not move. Below `sm` they go under the title.
 *
 * It takes **names and objects, not free nodes**, and that is the fix: when it took `actions` as a
 * `ReactNode`, each screen put in whatever it had — an outline button, a small one, two underlined
 * help links, a ghost text — and the same header came out six different ways. A screen that needs
 * more than one action puts the others in its content, next to what they act on.
 *
 * The title uses `.page-title`; a screen whose top is not this shape uses the class directly instead
 * of bending this component.
 *
 * @param props.title       the screen's name
 * @param props.description optional one-line purpose
 * @param props.badge       optional qualifier next to the title
 * @param props.back        optional screen to return to
 * @param props.help        optional help section for «Cómo funciona»
 * @param props.action      optional main action
 */
export function PageHeader({ title, description, badge, back, help, action }: PageHeaderProps) {
  return (
    <div className="space-y-2">
      {back && (
        <Link to={back.to} className="text-fg-muted hover:text-fg inline-flex items-center gap-1 text-sm">
          <ArrowLeft className="size-4" aria-hidden="true" />
          {back.label}
        </Link>
      )}
      {/* Never `flex-wrap` on this row: a long description wrapped the buttons onto a line of their
          own, flush left, where they looked dropped. From `sm` up the buttons keep the right edge and
          the text makes room; below it they go under the title on purpose. */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between sm:gap-6">
        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="page-title">{title}</h1>
            {badge}
          </div>
          {description && <p className="text-fg-muted text-sm">{description}</p>}
        </div>
        {(help || action) && (
          <div className="flex shrink-0 items-center gap-2">
            {help && <HelpButton section={help} />}
            {action && <PageActionButton action={action} />}
          </div>
        )}
      </div>
    </div>
  )
}

/** The main action as a button: a link when it goes somewhere, a click when it happens here. */
function PageActionButton({ action }: { action: PageAction }) {
  if (action.to !== undefined && !action.disabled) {
    return (
      <Button asChild>
        <Link to={action.to}>{action.label}</Link>
      </Button>
    )
  }
  return (
    <Button type="button" disabled={action.disabled ?? false} onClick={action.onClick}>
      {action.label}
    </Button>
  )
}
