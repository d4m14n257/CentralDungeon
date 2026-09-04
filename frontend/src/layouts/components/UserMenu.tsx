import { CircleQuestionMark, Languages, Moon, Sun } from 'lucide-react'
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
import { LANGUAGES } from '@/config/language'
import { helpPath, paths } from '@/config/paths'
import { useLogout } from '@/features/auth'
import { useLanguage } from '@/hooks/useLanguage'
import { useAuth } from '@/providers/AuthProvider'

/**
 * Avatar, language, theme and sign out (frontend-diseno.md §5, inventario de compuestos).
 *
 * Language sits next to theme because it is the same kind of thing (#198): a preference of the
 * person and not of the account, chosen once and remembered, with no server round trip.
 */
export function UserMenu({ displayName }: { displayName: string | null }) {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const { signOut } = useAuth()
  const logout = useLogout()
  const { resolvedTheme, setTheme } = useTheme()
  const { language, setLanguage } = useLanguage()

  // Before onboarding there is no name to show yet (#134); the avatar cannot be left empty.
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
        {/* The help lives here and not in the bar: it is read once and does not compete with the navigation. */}
        <DropdownMenuItem onSelect={() => void navigate(helpPath())}>
          <CircleQuestionMark className="size-4" />
          {t('nav.help')}
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        {/* Each language names itself — "English", never "Inglés": somebody looking for their own
            language does not necessarily read the one currently on screen (#198). */}
        <DropdownMenuLabel className="text-fg-muted text-xs font-normal">
          <span className="flex items-center gap-2">
            <Languages className="size-4" />
            {t('language.label')}
          </span>
        </DropdownMenuLabel>
        {LANGUAGES.map((code) => (
          <DropdownMenuItem key={code} onSelect={() => setLanguage(code)} aria-current={code === language ? 'true' : undefined}>
            <span className={code === language ? 'font-semibold' : undefined}>{t(`language.${code}`)}</span>
          </DropdownMenuItem>
        ))}
        <DropdownMenuSeparator />
        {/* The item names the action, not the current state: in dark mode it reads "Light theme". */}
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
