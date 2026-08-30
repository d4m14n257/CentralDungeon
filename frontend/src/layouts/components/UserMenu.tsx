import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'

import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useLogout } from '@/features/auth'
import { paths } from '@/config/paths'
import { useAuth } from '@/providers/AuthProvider'

export function UserMenu({ displayName }: { displayName: string }) {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const { signOut } = useAuth()
  const logout = useLogout()

  const initials = displayName
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

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
        <Button variant="ghost" size="icon" className="rounded-full" aria-label={displayName}>
          <Avatar>
            <AvatarFallback>{initials}</AvatarFallback>
          </Avatar>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onSelect={handleLogout}>{t('actions.logout')}</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
