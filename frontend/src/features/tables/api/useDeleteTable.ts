import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { gameTablesApi } from './gameTablesApi'

/**
 * Borrar el borrador de una mesa que nunca fue pública (decisiones.md #175). No hay detalle que
 * actualizar después: la mesa deja de existir para todos, así que se limpia su caché y se
 * invalidan los listados donde estaba.
 */
export function useDeleteTable(tableId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => gameTablesApi.delete(tableId),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: queryKeys.tables.managedDetail(tableId) })
      queryClient.removeQueries({ queryKey: queryKeys.tables.detail(tableId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tables.managed() })
      void queryClient.invalidateQueries({ queryKey: ['tables', 'admin'] })
    },
  })
}
