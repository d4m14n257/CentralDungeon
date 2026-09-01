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
import { masterTablesPath, paths } from '@/config/paths'
import type { AppContext } from '@/stores/contextStore'
import { useContextStore } from '@/stores/contextStore'

const CHIP_CLASSES = 'border-border-strong text-fg-muted h-auto gap-1.5 rounded-lg border px-3 py-1.5 text-sm font-normal'

/**
 * UI organization only, never authorization (#103) - the backend authorizes every endpoint on
 * its own. Con un solo contexto no hay nada que elegir, así que el chip queda sin caret ni menú
 * (#144) - sigue mostrando en qué contexto estás, pero no sugiere una interacción que no existe.
 */
export function ContextSwitcher({ availableContexts }: { availableContexts: AppContext[] }) {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const { activeContext, setActiveContext } = useContextStore()

  if (availableContexts.length === 0) {
    return null
  }

  if (availableContexts.length === 1) {
    return <span className={`${CHIP_CLASSES} inline-flex items-center`}>{t(`nav.${availableContexts[0]}`)}</span>
  }

  function switchTo(context: AppContext) {
    setActiveContext(context)
    navigate(context === 'master' ? masterTablesPath() : paths.home)
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        {/* Chip, no botón: el relleno sólido está reservado al acento (frontend-diseno.md 3). */}
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
