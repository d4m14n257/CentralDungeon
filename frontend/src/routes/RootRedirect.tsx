import { useTranslation } from 'react-i18next'
import { Navigate } from 'react-router'

import { homePathFor } from '@/config/paths'
import { useAvailableContexts } from '@/hooks/useAvailableContexts'

/**
 * `/` has no screen of its own (#222). Every context owns a prefix, so the root is a dispatcher:
 * it sends the reader to the home of the context they actually have.
 *
 * It exists so that nothing else has to know where somebody lands - the OAuth return, the
 * onboarding and the 404 all point at `/` and let this decide, instead of each recomputing the same
 * answer. It is also what an old bookmark hits.
 *
 * It waits for the roles before deciding. Redirecting on the first render would send everybody to
 * the player home, because that is the fallback while `/users/me` is still in flight - and a master
 * who has no Player role would land in a context they do not have, which is the exact crossing this
 * dispatcher exists to prevent.
 */
export function RootRedirect() {
  const { t } = useTranslation('common')
  const { preferredContext, isPending } = useAvailableContexts()

  if (isPending) {
    return (
      <div className="flex min-h-svh items-center justify-center">
        <span className="text-fg-muted text-sm">{t('states.loading')}</span>
      </div>
    )
  }

  return <Navigate to={homePathFor(preferredContext)} replace />
}

export { RootRedirect as Component }
