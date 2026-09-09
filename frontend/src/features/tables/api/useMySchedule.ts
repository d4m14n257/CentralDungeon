import { useQuery } from '@tanstack/react-query'

import { api } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import type { WeeklyCommitment } from '../types'

/**
 * The reader's whole week: everything they run and everything they play at, in one answer (#227).
 *
 * One query and not two, because the whole point of the screen is that the two halves collide with
 * each other — a master's Tuesday and a player's Tuesday are the same Tuesday. The server merges
 * them for the same reason #178 does when it refuses a clash.
 *
 * @returns the query for the reader's weekly commitments, in UTC minutes from Monday 00:00
 */
export function useMySchedule() {
  return useQuery({
    queryKey: queryKeys.schedule.mine(),
    queryFn: () => api.get<WeeklyCommitment[]>('/api/v1/users/me/schedule'),
    staleTime: staleTime.tableList,
  })
}
