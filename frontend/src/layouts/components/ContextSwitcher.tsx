import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import type { AppContext } from '@/stores/contextStore'
import { useContextStore } from '@/stores/contextStore'

/** UI organization only, never authorization (#103) - the backend authorizes every endpoint on its own. */
export function ContextSwitcher({ availableContexts }: { availableContexts: AppContext[] }) {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const { activeContext, setActiveContext } = useContextStore()

  if (availableContexts.length < 2) {
    return null
  }

  function switchTo(context: AppContext) {
    setActiveContext(context)
    navigate(context === 'master' ? '/master/tables' : '/')
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm">
          {t(`nav.${activeContext}`)}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        {availableContexts.map((context) => (
          <DropdownMenuItem key={context} onSelect={() => switchTo(context)}>
            {t(`nav.${context}`)}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
