import { useQuery } from '@tanstack/react-query'

import { api } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'

interface HealthResponse {
  status: string
}

const POLL_INTERVAL_MS = 15_000

/**
 * Sondea GET /api/v1/health, público y sin dependencias (docs/decisiones.md #146) - funciona
 * incluso en /login, antes de tener sesión. `retry: false` a propósito: un solo fetch fallido ya
 * alcanza para marcar "sin conexión", reintentar de a poco por debajo solo demoraría el aviso.
 * El sondeo periódico es el reintento real.
 */
export function useBackendStatus() {
  const { isError } = useQuery({
    queryKey: queryKeys.system.health(),
    queryFn: () => api.get<HealthResponse>('/api/v1/health'),
    refetchInterval: POLL_INTERVAL_MS,
    retry: false,
    staleTime: 0,
    gcTime: POLL_INTERVAL_MS,
  })

  // Antes de la primera respuesta se asume online: no hay motivo para alarmar mientras carga.
  return { isOnline: !isError }
}
