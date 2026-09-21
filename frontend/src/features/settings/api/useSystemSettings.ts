import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { settingsApi } from './settingsApi'

/**
 * Every setting the platform has, for `/admin/settings` (#141).
 *
 * Reads the live values rather than anything cached on the server: this answers "what is configured,
 * by whom and when", which somebody is looking at in order to change it — a cached listing would
 * show them the value they just replaced.
 *
 * @returns the query, one row per setting, grouped by category in the order the screen reads them
 */
export function useSystemSettings() {
  return useQuery({
    queryKey: queryKeys.settings.list(),
    queryFn: () => settingsApi.list(),
  })
}
