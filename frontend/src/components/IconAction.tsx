import type { ComponentProps, ReactNode } from 'react'

import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'

interface IconActionProps extends Omit<ComponentProps<typeof Button>, 'children' | 'size'> {
  /** What the action does, in words. It is both the tooltip and the accessible name — an icon alone says nothing. */
  label: string
  /** The icon. Marked `aria-hidden` here, so a screen reader announces `label` and not two things. */
  icon: ReactNode
}

/**
 * An icon button with its tooltip, for the actions of a row or a card
 * (`frontend-diseno.md` §5) — the replacement for the legacy `ActionButtonDefault`.
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
 * @param props.label what the action does, already passed through `t()`
 * @param props.icon  the icon to show
 */
export function IconAction({ label, icon, ...props }: IconActionProps) {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button type="button" variant="ghost" size="icon" aria-label={label} {...props}>
            <span aria-hidden="true" className="inline-flex">
              {icon}
            </span>
          </Button>
        </TooltipTrigger>
        <TooltipContent>{label}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
