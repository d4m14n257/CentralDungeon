import { useInfiniteQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { gameTablesApi } from './gameTablesApi'

/**
 * El explorador trae de a una página y suma con "Ver más" (decisiones.md #173): es un listado para
 * mirar, no para recorrer por número de página. `useInfiniteQuery` acumula las páginas ya traídas,
 * así que volver atrás en el navegador no vuelve a pedirlas.
 */
export function useGameTables() {
  return useInfiniteQuery({
    queryKey: queryKeys.tables.list(),
    queryFn: ({ pageParam }) => gameTablesApi.list(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined),
    staleTime: staleTime.tableList,
  })
}
