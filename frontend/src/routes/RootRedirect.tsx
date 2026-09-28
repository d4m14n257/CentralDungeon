import { useTranslation } from 'react-i18next'
import { Navigate } from 'react-router'

import { EmptyState } from '@/components/EmptyState'
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
 *
 * It is also the one place an account with **no** context lands, since every context guard sends
 * what it refuses here (#269) - so that case is answered on screen instead of redirected.
 */
export function RootRedirect() {
  const { t } = useTranslation('common')
  const { contexts, preferredContext, isPending } = useAvailableContexts()

  if (isPending) {
    return (
      <div className="flex min-h-svh items-center justify-center">
        <span className="text-fg-muted text-sm">{t('states.loading')}</span>
      </div>
    )
  }

  // An account with no context at all - every role revoked (#241) - has no home to go to. Navigating
  // anyway would send it to `/player`, whose guard sends it back here (#269): a loop, not an answer.
  if (contexts.length === 0) {
    return (
      <div className="flex min-h-svh items-center justify-center px-4">
        <EmptyState title={t('states.noContextTitle')} description={t('states.noContextDescription')} />
      </div>
    )
  }

  return <Navigate to={homePathFor(preferredContext)} replace />
}

export { RootRedirect as Component }
