import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'
import { staleTime } from '@/config/query'

import { usersApi } from './usersApi'

/**
 * Buscador de personas (#164, #165). `keepPreviousData` evita que la lista parpadee entre teclas:
 * los resultados viejos se quedan en pantalla, atenuados, hasta que llegan los nuevos.
 */
export function useUserSearch(query: string, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.users.search(query),
    queryFn: () => usersApi.search(query),
    enabled,
    staleTime: staleTime.profile,
    placeholderData: keepPreviousData,
  })
}
