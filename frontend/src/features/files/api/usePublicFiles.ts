import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { filesApi } from './filesApi'
import type { FileCategory } from '../types'

/**
 * What the platform published (#64) — the list a master picks the community's default sheet from.
 *
 * It is the whole point of #79: attaching one of these links it instead of copying it, so correcting
 * the document corrects every table that uses it.
 *
 * **Narrowed by cajón and no longer by audience** (#233, which derogated #64): the flow already says
 * who a document is for, so a picker asks for the cajón it is standing in and gets the community's
 * blanks for exactly that moment.
 *
 * @param category the cajón to narrow to, or undefined for everything published
 * @param enabled  false to hold the request back until the picker is actually open
 * @returns the query for the published files
 */
export function usePublicFiles(category?: FileCategory, enabled = true) {
  return useQuery({
    queryKey: queryKeys.files.public(category),
    queryFn: () => filesApi.listPublic(category),
    staleTime: staleTime.catalogs,
    enabled,
  })
}
