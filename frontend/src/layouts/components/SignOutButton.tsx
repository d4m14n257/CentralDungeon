import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'

import { Button } from '@/components/ui/button'
import { paths } from '@/config/paths'
import { useLogout } from '@/features/auth'
import { useAuth } from '@/providers/AuthProvider'

/**
 * The way out for a screen that has no header: closes the session and goes to the login.
 *
 * It signs out locally even when the server call fails, so a dead backend cannot trap somebody in a
 * session they want to leave.
 */
export function SignOutButton() {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const { signOut } = useAuth()
  const logout = useLogout()

  return (
    <Button
      variant="ghost"
      size="sm"
      disabled={logout.isPending}
      onClick={() =>
        logout.mutate(undefined, {
          onSettled: () => {
            signOut()
            navigate(paths.login)
          },
        })
      }
    >
      {t('actions.logout')}
    </Button>
  )
}
