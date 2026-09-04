import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { filesApi } from './filesApi'
import type { PublicAudience } from '../types'

/**
 * What the platform published (#64) — the list a master picks the community's default sheet from.
 *
 * It is the whole point of #79: attaching one of these links it instead of copying it, so correcting
 * the document corrects every table that uses it.
 *
 * @param audience who to narrow to, or undefined for everything published
 * @param enabled  false to hold the request back until the picker is actually open
 * @returns the query for the published files
 */
export function usePublicFiles(audience?: PublicAudience, enabled = true) {
  return useQuery({
    queryKey: queryKeys.files.public(audience),
    queryFn: () => filesApi.listPublic(audience),
    staleTime: staleTime.catalogs,
    enabled,
  })
}
