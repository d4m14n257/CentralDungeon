import { useId, type ComponentProps, type ReactNode } from 'react'

import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'

interface IconActionProps extends Omit<ComponentProps<typeof Button>, 'children' | 'size'> {
  /** What the action does, in words. It is both the tooltip and the accessible name — an icon alone says nothing. */
  label: string
  /** The icon. Marked `aria-hidden` here, so a screen reader announces `label` and not two things. */
  icon: ReactNode
  /**
   * Why the action cannot be taken right now, already passed through `t()`. When present the button is
   * shown switched off and its tooltip says this instead of `label`.
   *
   * It exists because a row's action is either absent or explained, never silently greyed out
   * (principio 2 de frontend-diseno.md §1). A native `disabled` cannot carry the explanation: the
   * button stops receiving the pointer and the tooltip never opens. So this one stays focusable and
   * hoverable, is marked `aria-disabled`, and ignores clicks.
   */
  disabledReason?: string | undefined
}

/**
 * An icon button with its tooltip, for the actions of a row or a card
 * (skill `diseno` §5) — the replacement for the legacy `ActionButtonDefault`.
 *
 * **The text is not decoration**: it travels as `aria-label` as well as a tooltip, because an icon
 * with no accessible name is a button that does not exist for anyone who cannot see it. And the
 * tooltip is not the only way to find out what the button does: there is no hover on touch, so the
 * name has to be in the DOM regardless.
 *
 * The `TooltipProvider` lives in here rather than in a layout: with `delayDuration` at 0 there is no
 * shared delay to gain by hoisting it, and this way the component works in any tree — a test's
 * included — without asking its callers to remember to mount anything.
 *
 * @param props.label          what the action does, already passed through `t()`
 * @param props.icon           the icon to show
 * @param props.disabledReason why it cannot be taken now, if it cannot
 */
export function IconAction({ label, icon, disabledReason, onClick, className, ...props }: IconActionProps) {
  const reasonId = useId()
  const blocked = disabledReason !== undefined
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label={label}
            aria-disabled={blocked || undefined}
            aria-describedby={blocked ? reasonId : undefined}
            {...props}
            className={cn(blocked && 'cursor-not-allowed opacity-50', className)}
            onClick={blocked ? undefined : onClick}
          >
            <span aria-hidden="true" className="inline-flex">
              {icon}
            </span>
            {blocked && (
              <span id={reasonId} className="sr-only">
                {disabledReason}
              </span>
            )}
          </Button>
        </TooltipTrigger>
        <TooltipContent className="max-w-64">{disabledReason ?? label}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
