import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'

export function useGameTable(id: string) {
  return useQuery({
    queryKey: queryKeys.tables.detail(id),
    queryFn: () => gameTablesApi.byId(id),
    staleTime: staleTime.tableDetail,
  })
}
