import { useMe } from '@/features/users'

/** Whether this account has a library of its own, and whether the answer has arrived yet. */
export interface PersonalLibraryAccess {
  hasPersonalLibrary: boolean
  /** Until the profile lands the answer is a guess: anything that *acts* on it has to wait. */
  isPending: boolean
}

/**
 * Whether this account is one that fills a personal library: a player or a master (#241).
 *
 * **Decided by role**, like the contexts of the switcher (`useAvailableContexts`) and like everything
 * else the shell shows or hides: `me.roles` is for deciding *what to show*, never what to allow —
 * authorization is the backend's, endpoint by endpoint (#103).
 *
 * `Player` is not a given. Every account is **created** with it (#38), which is a statement about the
 * moment of signup and not an invariant: a role can be revoked, so somebody can end up holding only
 * `Admin`, only `Master`, or only `Player`. An account that is only an admin or the owner never goes
 * through the flows that fill a library — an application, a submission, a table's material — so it
 * has none, and what the platform publishes is `/admin/files`, a different screen (#237).
 *
 * `hasManagedTables` is the same exception the Master context makes (#135): somebody an admin
 * assigned to a single table runs it without ever being granted the role, and their table's material
 * is theirs.
 *
 * @returns whether they have one, and whether the profile has arrived
 */
export function useHasPersonalLibrary(): PersonalLibraryAccess {
  const { data: me, isPending } = useMe()
  const plays = me?.roles.includes('Player') ?? false
  const runsTables = (me?.roles.includes('Master') ?? false) || (me?.hasManagedTables ?? false)

  return { hasPersonalLibrary: plays || runsTables, isPending }
}
