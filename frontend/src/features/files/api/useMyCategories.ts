import { useQuery } from '@tanstack/react-query'

import { staleTime } from '@/config/query'

import { filesApi } from './filesApi'

/**
 * The cajones this person may file something of their own under (#237).
 *
 * **Asked of the server rather than derived from `me.roles`.** The rule depends on the actor's roles
 * *and* on whether they run any table — a co-master an admin assigned has no `Master` role at all
 * (#135) — and re-deriving that here is how a select ends up offering an option the server rejects.
 *
 * @returns the query for the cajones they may use. **It can come back empty**: #38 creates every
 *          account with `Player`, which is about the moment of signup and not an invariant — a role
 *          can be revoked, so somebody can end up holding only `Admin`. Whether the screen exists at
 *          all for them is a coarser question, and `useHasPersonalLibrary` answers it (#241)
 */
export function useMyCategories() {
  return useQuery({
    queryKey: ['files', 'mine', 'categories'],
    queryFn: () => filesApi.listMyCategories(),
    staleTime: staleTime.catalogs,
  })
}
