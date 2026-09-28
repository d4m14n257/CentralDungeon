import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate } from 'react-router'

import { paths } from '@/config/paths'
import { useAvailableContexts } from '@/hooks/useAvailableContexts'
import type { AppContext } from '@/stores/contextStore'

/** What the guard needs. */
interface RequireContextProps {
  /** The context whose prefix this subtree lives under. */
  context: AppContext
  /** The context's shell, rendered only for an account that has the context. */
  children: ReactNode
}

/**
 * Keeps an account out of a context it does not have (#269): a Player who types `/admin/users`, or
 * a master without the `Player` role who types `/player`, never sees that context's navigation or
 * screens - they are sent to `/`, which dispatches them to the home of a context they do have (#222).
 *
 * **A redirect, not a 403 on screen**: the reader forced a door that is not theirs, and the useful
 * answer is to put them back where they can work, not to explain the door. **It is still not the
 * security**: the backend authorizes endpoint by endpoint and answers `403` regardless (#103, #121);
 * this only decides what the interface shows. `ForbiddenState` stays for the other refusal - a
 * concrete resource inside a context the reader does have, but that is not theirs.
 *
 * Which contexts an account has is the same answer the switcher uses (`useAvailableContexts`), so
 * the chip and the gate can never disagree. It waits for the roles before deciding, for the same
 * reason `RootRedirect` does: deciding on the first render would bounce everybody.
 *
 * @param props.context  the context this subtree belongs to
 * @param props.children the context's shell
 */
export function RequireContext({ context, children }: RequireContextProps) {
  const { t } = useTranslation('common')
  const { contexts, isPending } = useAvailableContexts()

  if (isPending) {
    return (
      <div className="flex min-h-svh items-center justify-center">
        <span className="text-fg-muted text-sm">{t('states.loading')}</span>
      </div>
    )
  }

  if (!contexts.includes(context)) {
    return <Navigate to={paths.root} replace />
  }

  return children
}
