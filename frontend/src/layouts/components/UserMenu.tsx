import { CircleQuestionMark, Moon, Sun } from 'lucide-react'
import { useTheme } from 'next-themes'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'

import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { helpPath, paths } from '@/config/paths'
import { useLogout } from '@/features/auth'
import { useAuth } from '@/providers/AuthProvider'

/** Avatar, tema y cerrar sesión (frontend-diseno.md 5, inventario de compuestos). */
export function UserMenu({ displayName }: { displayName: string | null }) {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const { signOut } = useAuth()
  const logout = useLogout()
  const { resolvedTheme, setTheme } = useTheme()

  // Antes de onboarding todavía no hay nombre a mostrar (#134); el avatar no puede quedar vacío.
  const label = displayName ?? t('nav.accountFallback')
  const initials = label
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

  const isDark = resolvedTheme !== 'light'

  function handleLogout() {
    logout.mutate(undefined, {
      onSettled: () => {
        signOut()
        navigate(paths.login)
      },
    })
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="rounded-full" aria-label={label}>
          <Avatar className="size-8">
            <AvatarFallback className="text-xs font-semibold">{initials}</AvatarFallback>
          </Avatar>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-52">
        <DropdownMenuLabel className="truncate">{label}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {/* La ayuda vive acá y no en la barra: se consulta una vez y no compite con la navegación. */}
        <DropdownMenuItem onSelect={() => void navigate(helpPath())}>
          <CircleQuestionMark className="size-4" />
          {t('nav.help')}
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        {/* El ítem nombra la acción, no el estado actual: en oscuro dice "Tema claro". */}
        <DropdownMenuItem onSelect={() => setTheme(isDark ? 'light' : 'dark')}>
          {isDark ? <Sun className="size-4" /> : <Moon className="size-4" />}
          {isDark ? t('theme.switchToLight') : t('theme.switchToDark')}
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={handleLogout}>{t('actions.logout')}</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
