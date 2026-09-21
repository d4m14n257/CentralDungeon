import { useQuery } from '@tanstack/react-query'

import { api } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'
import { DEFAULT_CLAIM_TIMEOUT_MINUTES, DEFAULT_MAX_FILE_BYTES } from '@/config/limits'
import type { ClientLimits } from '@/types/api'

/**
 * The platform limits the interface states **before** somebody breaks one (principio 2 de
 * frontend-diseno.md §1).
 *
 * **Why it exists.** Until F3.5 the per-file cap and the tray's reservation timeout were constants on
 * both sides that happened to agree. Now an admin can change either from `/admin/settings` without a
 * deploy (#141), and a hardcoded mirror becomes a client that refuses files the server would take —
 * or a help page promising fifteen minutes when the platform gives thirty. Asking the server is what
 * keeps the two in step.
 *
 * **It lives in `hooks/` and not in a feature** (regla dura 16). A feature never imports another
 * one, and these are read by `features/files`, `features/help` and `/admin/queue` while the screen
 * that edits them is `features/settings` — so the shared thing is not domain at all: it is platform
 * configuration, the same shelf `useBackendStatus` sits on.
 *
 * **It never suspends and never fails a screen.** The compile-time default answers while the query
 * is in flight and if it never comes back, so a dropzone always has a number to check against — a
 * slightly stale cap costs one refused upload with the server's own message, where a dropzone
 * waiting on a fetch costs the whole screen.
 *
 * @returns the limits a client states rules with. Never the authority: the server applies the same
 *          cap and answers `FILE_TOO_LARGE` with the real number (#197), and the release job is what
 *          actually hands a stale reservation back
 */
export function useClientLimits(): ClientLimits {
  const { data } = useQuery({
    queryKey: queryKeys.system.limits(),
    queryFn: () => api.get<ClientLimits>('/api/v1/settings/limits'),
    // An admin changes a limit a handful of times a year, so re-asking on every mount would be a
    // request nobody's answer depends on. Long, not infinite: a session left open for a day should
    // still catch up with a cap that moved.
    staleTime: 60 * 60 * 1000,
  })

  return data ?? { maxFileSizeBytes: DEFAULT_MAX_FILE_BYTES, claimTimeoutMinutes: DEFAULT_CLAIM_TIMEOUT_MINUTES }
}
