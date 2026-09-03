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
 * Los códigos de error cuyo `detail` se muestra tal cual, porque el backend lo escribió para que
 * alguien lo lea y no para un log.
 *
 * Es una lista corta a propósito: el mensaje del servidor solo llega al usuario cuando el servidor
 * se comprometió a redactarlo. `SCHEDULE_CONFLICT` (#178) es el primero — un choque de horarios se
 * rechaza nombrando la mesa con la que choca, y decir «no se pudo guardar» ahí sería esconder
 * justamente lo único que la persona puede resolver (principio 2 de frontend-diseno.md 1).
 */
const EXPLAINED_ERROR_CODES = new Set(['SCHEDULE_CONFLICT'])

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
  if (!(error instanceof ApiError)) {
    toast.error(i18n.t('errors.offline'))
    return
  }
  toast.error(EXPLAINED_ERROR_CODES.has(error.problem.errorCode) ? error.problem.detail : i18n.t('errors.mutationFailed'))
}

/**
 * The application's single QueryClient.
 *
 * Two decisions live in it. Queries get an explicit `staleTime` instead of the library's default of
 * 0, which is what produces the steady drip of refetches; and every mutation without its own
 * `onError` gets a toast, so a failed write never ends in silence.
 */
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
