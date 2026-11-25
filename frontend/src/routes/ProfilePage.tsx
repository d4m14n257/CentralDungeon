import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { ErrorState } from '@/components/ErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { SubmitRequestSection } from '@/features/approvals'
import { HelpButton, HelpLink } from '@/features/help'
import { EditProfileDialog, ProfileCard, useMyProfile } from '@/features/users'

/**
 * `/profile` — the reader's own profile: name, country, roles and aggregate attendance.
 *
 * There is no "sin permiso" state here: nobody can be locked out of their own profile, which is
 * exactly what tells this screen apart from {@link import('./UserProfilePage').UserProfilePage}.
 * **Name and country are edited from here**, in a dialog that reuses the onboarding
 * fields; the first fill-in is the blocking `/onboarding` step, not this.
 * The lower half of the wireframe, "Received comments", is not built yet and this screen never draws it.
 *
 * **Asking to run tables happens here** and not on a screen for making requests:
 * this is where somebody sees which roles they have, so it is where the
 * absence of one is noticed. Two requests are offered side by side: the master role, to create tables
 * of one's own, and a single table, whose approval creates a draft the reader runs without the role.
 * Both are offered only to somebody who does not already hold the role — the backend refuses the
 * rest with `MASTER_ROLE_ALREADY_HELD`, and a button whose only outcome is a `409` is a button that
 * should not be there. The same rule hides each one while a request of
 * that kind is still pending, which the section resolves itself.
 */
export function ProfilePage() {
  const { t } = useTranslation('users')
  const { t: tAdmin } = useTranslation('admin')
  const { data, isPending, isLoadingError, refetch } = useMyProfile()
  const [editing, setEditing] = useState(false)

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

  const isMaster = data.roles.includes('Master')
  // The help arrives as a node, because a feature never imports another one.
  const requestHelp = (
    <p className="text-fg-subtle text-xs">
      {tAdmin('requests.submitHelpHint')} <HelpLink section="basics.requests">{tAdmin('requests.submitHelpLink')}</HelpLink>
    </p>
  )

  return (
    <div className="space-y-3">
      {/* «Who sees this» is the question the screen raises and cannot answer alone: the expiry
          of visibility after a table closes is not something anybody guesses by looking at a profile they can see today. */}
      <ProfileCard
        profile={data}
        isOwn
        viewerIsPlayer={data.roles.includes('Player')}
        actions={
          <div className="flex items-center gap-1">
            <HelpButton section="players.profile" />
            <Button type="button" onClick={() => setEditing(true)}>
              {t('profile.edit')}
            </Button>
          </div>
        }
      />
      <EditProfileDialog profile={data} open={editing} onOpenChange={setEditing} />
      {/* Nothing at all for somebody who already runs tables: the role is not something you can hold
          twice, so there is no request to make and nothing to explain. */}
      {!isMaster && (
        <div className="border-border space-y-2 rounded-lg border border-dashed p-4">
          <p className="text-sm font-medium">{tAdmin('requests.masterGrantPitchTitle')}</p>
          <p className="text-fg-muted text-sm">{tAdmin('requests.masterGrantPitchDescription')}</p>
          <div className="flex flex-wrap items-center gap-2">
            <SubmitRequestSection type="MasterGrant" help={requestHelp} />
            <SubmitRequestSection type="TableOpen" help={requestHelp} />
          </div>
        </div>
      )}
    </div>
  )
}

export { ProfilePage as Component }
