import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'

export function useGameTables(page = 0) {
  return useQuery({
    queryKey: queryKeys.tables.list({ page }),
    queryFn: () => gameTablesApi.list(page),
    staleTime: staleTime.tableList,
    placeholderData: keepPreviousData,
  })
}
