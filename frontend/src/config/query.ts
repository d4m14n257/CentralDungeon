import { MutationCache, QueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import i18n from '@/providers/i18n'
import { ApiError } from '@/types/api'

/** Per-data staleTime policy (arquitectura.md 3.3) - the default of 0 is what causes request goteo. */
export const staleTime = {
  catalogs: 60 * 60 * 1000,
  tableList: 30_000,
  tableDetail: 60_000,
  notifications: Infinity,
  profile: 5 * 60 * 1000,
} as const

/**
 * Red de seguridad para toda mutación que no trae su propio `onError`: hoy ninguna lo trae, así
 * que una escritura que falla (backend caído, 500, lo que sea) no decía nada - el botón dejaba
 * de estar "pending" y ahí terminaba, sin que quien lo tocó se enterara. Las queries no pasan por
 * acá: sus cuatro estados obligatorios (frontend-diseno.md 5) ya muestran el error en la propia
 * pantalla, y duplicarlo acá sería el mismo aviso dos veces.
 *
 * Si una mutación puntual necesita un mensaje más específico, agrega su propio `onError` en el
 * `useMutation` - React Query llama a los dos, no reemplaza este.
 */
function reportMutationError(error: unknown) {
  const isOffline = !(error instanceof ApiError)
  toast.error(isOffline ? i18n.t('errors.offline') : i18n.t('errors.mutationFailed'))
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: staleTime.tableList,
      retry: 1,
    },
  },
  mutationCache: new MutationCache({
    onError: reportMutationError,
  }),
})
