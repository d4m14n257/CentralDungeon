import { ChevronDown } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'

import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { homePathFor } from '@/config/paths'
import type { AppContext } from '@/stores/contextStore'

const CHIP_CLASSES = 'border-border-strong text-fg-muted h-auto gap-1.5 rounded-lg border px-3 py-1.5 text-sm font-normal'

/** What the chip shows and what the menu offers. */
interface ContextSwitcherProps {
  /** The contexts this account can enter. With one there is nothing to choose; with none there is no chip. */
  availableContexts: AppContext[]
  /** The context on screen, resolved from the URL by the header (#222). This is what the chip reads. */
  activeContext: AppContext
}

/**
 * UI organization only, never authorization (#103) - the backend authorizes every endpoint on
 * its own. With a single context there is nothing to choose, so the chip has neither a caret nor a
 * menu (#144) - it still says which context you are in, without suggesting an interaction that does
 * not exist.
 *
 * The chip reports **where the reader is**, not what they last picked (#222). Those two used to be
 * the same value and drifted apart the moment somebody in the Master context opened a player screen:
 * the chip kept saying "Master" while the navigation underneath was the player's.
 *
 * @param props.availableContexts the contexts the account can enter
 * @param props.activeContext the context on screen
 */
export function ContextSwitcher({ availableContexts, activeContext }: ContextSwitcherProps) {
  const { t } = useTranslation('common')
  const navigate = useNavigate()

  if (availableContexts.length === 0) {
    return null
  }

  if (availableContexts.length === 1) {
    return <span className={`${CHIP_CLASSES} inline-flex items-center`}>{t(`nav.${activeContext}`)}</span>
  }

  function switchTo(context: AppContext) {
    void navigate(homePathFor(context))
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        {/* A chip and not a button: the solid fill is reserved for the accent (frontend-diseno.md 3). */}
        <Button variant="ghost" className={`${CHIP_CLASSES} hover:text-fg`}>
          {t(`nav.${activeContext}`)}
          <ChevronDown className="size-3.5" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-48">
        <DropdownMenuLabel>{t('nav.switchContext')}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {availableContexts.map((context) => (
          <DropdownMenuItem key={context} onSelect={() => switchTo(context)}>
            {t(`nav.${context}`)}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
