import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { registrationsApi } from './registrationsApi'

/**
 * Everything the signed-in person applied to, whatever came of it, one page at a time. Backs
 * /player/applications.
 *
 * The page is in the key: it used to be left out, so every page shared one cache entry and the
 * screen only ever showed the first. "Did I already apply here?" is not asked of this list: it comes
 * with each table, computed by the server (ADR-09 §1).
 *
 * @param page zero-based page number
 * @returns the query for that page of their applications
 */
export function useMyApplications(page = 0) {
  return useQuery({
    queryKey: queryKeys.registrations.mine(page),
    queryFn: () => registrationsApi.mine(page),
  })
}
