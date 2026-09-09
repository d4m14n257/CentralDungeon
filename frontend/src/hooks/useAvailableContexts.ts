import { useLocation } from 'react-router'

import { contextOfPath } from '@/config/paths'
import { useMe } from '@/features/users'
import { useContextStore, type AppContext } from '@/stores/contextStore'

/** What the shell needs to know about contexts: which ones exist for this account, and which one is on screen. */
export interface AvailableContexts {
  /** The contexts this account can enter, in the order the switcher lists them. */
  contexts: AppContext[]
  /**
   * The context the reader is looking at. It is the one the **URL** belongs to, so the chip reports
   * where they are instead of what they last chose (#222) - a master who opens `/player` reads
   * "Jugador", because that is where they are. On a transversal screen, where no context owns the
   * path, it falls back to the remembered one.
   */
  activeContext: AppContext
  /** Where this account goes when nothing in the URL says otherwise: the remembered context, if they still have it. */
  preferredContext: AppContext
  /**
   * Whether the account's roles have arrived. Until they do, `contexts` is empty and the other two
   * are a guess - anything that *acts* on the answer, rather than displaying it, has to wait.
   */
  isPending: boolean
}

/**
 * Resolves the reader's contexts from their roles and from the URL.
 *
 * The Master context appears with the platform role **or** with at least one live row in `masters`
 * (#135): somebody running a single table, assigned without the role, still runs it.
 *
 * It reads the remembered context rather than trusting it raw: `activeContext` is persisted in
 * localStorage, which knows nothing about which account is signed in, so switching accounts in the
 * same browser can leave the previous session's context behind (#156).
 *
 * @returns the contexts this account has, the one on screen, and the one to fall back to
 */
export function useAvailableContexts(): AvailableContexts {
  const { data: me, isPending } = useMe()
  const { pathname } = useLocation()
  const remembered = useContextStore((state) => state.activeContext)

  const contexts: AppContext[] = []
  if (me?.roles.includes('Player')) contexts.push('player')
  if (me?.roles.includes('Master') || me?.hasManagedTables) contexts.push('master')
  if (me?.roles.includes('Admin') || me?.roles.includes('Owner')) contexts.push('admin')

  const preferredContext = contexts.includes(remembered) ? remembered : (contexts[0] ?? 'player')

  return { contexts, activeContext: contextOfPath(pathname) ?? preferredContext, preferredContext, isPending }
}
