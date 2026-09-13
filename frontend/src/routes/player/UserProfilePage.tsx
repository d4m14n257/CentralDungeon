import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'

import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { Skeleton } from '@/components/ui/skeleton'
import { ProfileCard, useUserProfile } from '@/features/users'
import { ApiError } from '@/types/api'

/**
 * `/player/users/:id` — somebody else's profile, subject to the visibility rules of #41, #44 and
 * #47.
 *
 * The backend answers **404** both when the id never existed and when the two-week window of #44
 * has already closed (decisiones.md #249) — on purpose, so the two cases are indistinguishable on
 * the wire and probing ids cannot tell a real account from an expired relationship. This screen
 * still softens the copy: "you can no longer view this profile" tells the truth to whoever once had
 * a relationship with that person, and reveals nothing to anybody else that a plain 404 would not
 * already have (#249) — it never claims the profile "does not exist", which the veto's own 404
 * deliberately does (#29) and this one deliberately does not.
 */
export function UserProfilePage() {
  const { t } = useTranslation('users')
  const { id } = useParams<{ id: string }>()
  const userId = id ?? ''
  const { data, isPending, error, isLoadingError, refetch } = useUserProfile(userId)

  if (isPending) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-1/2" />
        <Skeleton className="h-48 w-full" />
      </div>
    )
  }

  // Not "no existe" (#29's veto reads that way on purpose): this 404 covers two very different
  // truths on the wire, and only this copy is safe to show to both (#249).
  if (error instanceof ApiError && error.status === 404) {
    return <ForbiddenState description={t('profile.expiredDescription')} />
  }

  if (isLoadingError || !data) {
    return <ErrorState message={t('profile.loadErrorDescription')} onRetry={() => void refetch()} />
  }

  return <ProfileCard profile={data} />
}

export { UserProfilePage as Component }
