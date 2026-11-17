import { useMutation, useQueryClient, type UseMutationResult } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import type { GameTableDetail } from '../types'
import { gameTablesApi } from './gameTablesApi'

/**
 * A master of a running table turning its search for players on or off.
 *
 * The answer is the table as it stands, so both of its cached details are replaced with it, and the
 * explorer is invalidated because whether the table lists there depends on this flag.
 *
 * @param tableId the table
 * @returns the mutation, taking whether the table should be looking for players
 */
export function useSetSeekingPlayers(tableId: string): UseMutationResult<GameTableDetail, Error, boolean> {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (seeking: boolean) => gameTablesApi.setSeekingPlayers(tableId, { seeking }),
    onSuccess: (table) => {
      queryClient.setQueryData(queryKeys.tables.managedDetail(tableId), table)
      queryClient.setQueryData(queryKeys.tables.detail(tableId), table)
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.lists() })
    },
  })
}
