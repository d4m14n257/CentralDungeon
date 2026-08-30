import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'

export function useMyTables(page = 0) {
  return useQuery({
    queryKey: queryKeys.tables.mine(),
    queryFn: () => gameTablesApi.mine(page),
    staleTime: staleTime.tableList,
  })
}
