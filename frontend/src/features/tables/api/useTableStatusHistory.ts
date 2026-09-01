import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'

export function useTableStatusHistory(id: string) {
  return useQuery({
    queryKey: queryKeys.tables.statusHistory(id),
    queryFn: () => gameTablesApi.statusHistory(id),
    staleTime: staleTime.tableDetail,
  })
}
