import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { registrationsApi } from './registrationsApi'

/**
 * The queue of pending candidates for a table, oldest first. FIFO is the backend's order and the
 * screen never re-sorts it: it is what decides who is auto-rejected when the table fills up (#28, #34).
 *
 * @param tableId the table whose queue to read
 * @returns the query for its candidates
 */
export function useCandidates(tableId: string, page = 0) {
  return useQuery({
    queryKey: queryKeys.registrations.candidates(tableId),
    queryFn: () => registrationsApi.candidates(tableId, page),
  })
}
