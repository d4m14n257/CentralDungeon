import { useQuery } from '@tanstack/react-query'

import { api } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'

interface HealthResponse {
  status: string
}

const POLL_INTERVAL_MS = 15_000

/**
 * Polls GET /api/v1/health, which is public and has no dependencies (docs/decisiones.md #146) - so
 * it works on /login too, before there is a session. `retry: false` on purpose: one failed fetch is
 * already enough to say "offline", and retrying quietly underneath would only delay the notice. The
 * periodic poll is the real retry.
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

  // Online is assumed until the first answer arrives: there is no reason to alarm anybody while it loads.
  return { isOnline: !isError }
}
