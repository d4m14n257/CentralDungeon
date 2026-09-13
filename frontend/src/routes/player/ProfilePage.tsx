import { useTranslation } from 'react-i18next'

import { ErrorState } from '@/components/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { HelpLink } from '@/features/help'
import { ProfileCard, useMyProfile } from '@/features/users'

/**
 * `/player/profile` — the reader's own profile: name, country, roles and aggregate attendance
 * (decisiones.md #248).
 *
 * There is no "sin permiso" state here: nobody can be locked out of their own profile, which is
 * exactly what tells this screen apart from {@link import('./UserProfilePage').UserProfilePage}.
 * The lower half of the wireframe, "Comentarios recibidos", is F5 and this screen never draws it
 * (§ ProfileCard).
 */
export function ProfilePage() {
  const { t } = useTranslation('users')
  const { data, isPending, isLoadingError, refetch } = useMyProfile()

  if (isPending) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-1/2" />
        <Skeleton className="h-48 w-full" />
      </div>
    )
  }

  if (isLoadingError || !data) {
    return <ErrorState message={t('profile.loadErrorDescription')} onRetry={() => void refetch()} />
  }

  return (
    <div className="space-y-3">
      <ProfileCard profile={data} />
      {/* «Quién ve esto» es la pregunta que provoca la pantalla y que la pantalla sola no contesta
          (#231): la caducidad de #44 no se adivina mirando un perfil que hoy se ve. */}
      <HelpLink section="players.profile" className="inline-block text-xs">
        {t('profile.help')}
      </HelpLink>
    </div>
  )
}

export { ProfilePage as Component }
